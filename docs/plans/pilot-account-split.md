# Plan: Pilot/Account split

**Status:** Designed and approved 2026-08-29; implemented 2026-08-29 on `feature/pilot-account-split`.
Everything in Design/Migration/New endpoints below is built and tested, including dropping
`pilot.email`/`pilot.disabled_at` in the same PR (see Migration section - no real data deployed yet,
so the usual contract-phase wait didn't apply). The "Explicitly out of scope" items at the bottom
remain exactly that - not part of this change.

**Correction post-implementation (2026-08-29):** the first pass of this implementation left
`DELETE /pilot/{pilotId}` and `DELETE /admin/pilot/{pilotId}` soft-deleting the `Pilot` row itself
(via `pilot.deleted_at`), unchanged from how they worked before this plan. Andy caught this: *"We
would disable/enable and delete accounts not pilots. That's the decision."* Consistent with
`email`/`disabled` moving to `Account`, delete belongs there too - a `Pilot`'s logged flight history
must survive its account being removed, exactly as it already survives an account never having
existed (the unclaimed case). Fixed to delete the `Account` row plus every `AuthIdentity` for that
pilot (not just disable them - see Design section 4 below), leaving `Pilot` completely untouched.
Also meant `pilot.deleted_at` itself was dead weight, since nothing sets it anymore - dropped in the
same follow-up (`V5__drop_pilot_deleted_at.sql`), same reasoning as dropping `pilot.email`/
`disabled_at` above (no real data deployed yet).

## Context

Today `Pilot` conflates three things: "a person recordable on a flight," "an account holder," and
"contactable by email." There's no way to record a co-pilot who hasn't signed up. Walked through the
actual desired flow with Andy:

> William creates an entry with Louis as his co-pilot. They would both have pilot ids, but Louis's
> is just created by William. If William later sends Louis a referral code, it links directly to the
> `PilotId` William already logged. If Louis isn't invited by William specifically, he can register
> and log his own entries under a **separate** new `PilotId` - a merge capability between the two
> comes later, out of scope here.

An earlier draft of this plan kept `email`/`disabled` on `Pilot` itself and just relaxed the email
uniqueness constraint to accommodate it. Andy pushed back: *"If the pilot is decoupled I don't think
we need active or email."* Every usage of both fields was checked before redesigning around removing
them - see Ground truth below. That check confirmed the pushback was right and actually simplifies
the whole design considerably.

Confirmed decisions:
- **Keep `Pilot`/`PilotId`** as the name for the base "person recordable on a flight" concept -
  "pilot" is domain-correct either way, with or without an app account.
- **`email` and `disabled` move off `Pilot` entirely**, onto a new `account` table (see Design).
- **`FlightEntry` actually referencing a co-pilot's `PilotId`** (rather than the existing free-text
  `pic_name`) is a separate, later plan (logbook entries) - this plan doesn't add or presume any
  particular shape for that.
- **This plan is backend-only** - confirmed `hobbs-ui` has zero dependency on `Pilot` carrying
  `email`/`disabled` in any response shape (checked its full `lib/` tree).

## Ground truth (verified against the code, not guessed)

- **`auth_identity.identifier` is already the real source of truth for login** - `Auth.login` looks
  up by `(type, identifier)`, never by `pilot.email`. `pilot.email` is presently just a second,
  redundant copy of the same string, populated once at registration.
- **They can already silently desync today** - `PilotEndpoint.updatePilot` (self-service `PUT
  /pilot/{pilotId}`) updates `pilot.email` via `Pilots.update` but never touches
  `auth_identity.identifier`. Pre-existing bug, independent of this redesign, but this work touches
  exactly the code that causes it - worth fixing as a byproduct (see Design, section 2).
- **`disabled` is real, enforced-at-login-time behavior**, not just an admin display flag -
  `Auth.login` (line ~88): `if (pilot == null || pilots.isDisabled(pilot.getId())) throw
  InvalidCredentialsException`. Confirmed by `AuthTest.throwsForDisabledPilot()`. (Separately:
  `SessionAuthFilter` does *not* re-check disabled status on every request today - disabling blocks
  future logins but doesn't kill an already-issued session. Pre-existing gap, not created by this
  plan, not fixed by it either - flagged for awareness only.)
- **Every production usage of `Pilot.getEmail()`/`isDisabled()` is account-shaped**: registration,
  login, password reset, admin invite/list/enable-disable. None of it is "who is this person on a
  flight" - the flight domain (`FlightEntry`, `Logbook`, `Aircraft`) never touches pilot email at
  all.
- **`hobbs-ui` has zero dependency on this.** Its only uses of "email" are the user typing their own
  email into register/login/reset-password forms (`RegisterDto`/`CreateSessionDto`/password-reset
  DTOs) - never reading it back off a `Pilot`/`PilotDto` response. It has no admin pilot-management
  screen at all (zero matches for "disabled" anywhere in `lib/`).
- **The `admin` table is the existing precedent for this exact shape** - `admin (pilot_id UUID
  PRIMARY KEY REFERENCES pilot(id))`, a tiny fact-table keyed directly by `pilot_id`, no synthetic
  ID. The new `account` table mirrors this exactly.
- `pilot.email` is already nullable at the DB level (`V1__initial_schema.sql`);
  `referral_code.invited_email` already models "an email for someone who doesn't have a `Pilot` row
  yet" - further precedent that email-as-a-transient-invite-detail (rather than a permanent `Pilot`
  attribute) is already how this schema thinks about the pre-account state.

## Design

### 1. `Pilot` becomes genuinely minimal

`pilot` table keeps only `id, name, deleted_at` (plus the new `created_by`, below) - `email` and
`disabled_at` stop being written/read by any new code (see the migration section for *when* the
columns themselves get dropped). `Pilots.create` drops its `email` parameter entirely:
`Pilots.create(name, createdBy)` - one creation path whether the pilot ends up with an account or
not; `createdBy` is `null` for self-registration, or the inviting pilot's ID for an unclaimed record
(see below). No separate `createUnclaimed` method needed - creation is identical either way, only
what happens *after* creation differs.

New column: `pilot.created_by UUID NULL REFERENCES pilot(id)` - who created this record. Needed for
authorization in section 3 - only the creator (or an admin) can issue a claim-invite for a `Pilot`
they created.

New endpoint: `POST /pilot` (authenticated, not admin-gated) - body `{name}` → 201 `{id, name}`.
Creates an unclaimed `Pilot`, `created_by` = caller. `PilotEndpoint.java` is the natural home.

### 2. New `account` table - email + disabled, keyed by `pilot_id`

```sql
CREATE TABLE account (
    pilot_id     UUID PRIMARY KEY REFERENCES pilot(id),
    email        VARCHAR(255) NOT NULL,
    disabled_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT account_email_unique UNIQUE (email)
);
```

A `Pilot` has an account iff a row exists here - no separate boolean needed anywhere. New
`Account`/`Accounts`/`AccountRepository` classes, same shape as the existing
`Admin`/`AdminRepository` pair: `create(pilotId, email)`, `updateEmail(pilotId, newEmail)`,
`disable(pilotId)`, `enable(pilotId)`, `isDisabled(pilotId)`, `findByEmail(email)`, `get(pilotId)`
(nullable).

**`updateEmail` also updates the `PASSWORD` `AuthIdentity.identifier` in the same call** - fixes the
pre-existing desync bug noted above as a direct byproduct of centralizing email onto `Account`.

### 3. Claiming an existing unclaimed `Pilot` via a scoped referral code

New column: `referral_code.claims_pilot_id UUID NULL REFERENCES pilot(id)`.

New endpoint: `POST /pilot/{pilotId}/invite` (authenticated, **not admin-only** - this is William's
mechanism to invite Louis). Body `{email}`. Authorization: 403 unless the caller created that
`pilotId` (`pilot.created_by == authenticatedPilotId`) or is an admin. Generates a `ReferralCode`
exactly like `AdminEndpoint.invitePilot`'s existing pattern, except with `claimsPilotId` set - the
invited email is captured on the `ReferralCode` (as it already is for every invite today), never on
`Pilot` itself, consistent with `Pilot` no longer carrying email at all.

`Auth.register` (updated):
```
validate password
resolve bootstrap-or-referral code (unchanged)
if referralCode?.claimsPilotId != null:
    pilot = pilots.get(claimsPilotId)
    pilots.updateName(pilot.getId(), name)   // self-asserted name wins over William's guess
else:
    pilot = pilots.create(name, createdBy = null)
accounts.create(pilot.getId(), email)         // NEW - was implicit in pilots.create before
hash password; authIdentityRepository.save(AuthIdentity(..., pilot.getId(), PASSWORD, email, hash))
mark admin/referral used; touchLastLogin; sessions.create(pilot)
```

When `claimsPilotId` is null (today's only case), the net effect is identical to today's behavior -
existing registration/bootstrap/admin-invite tests should all keep passing unchanged, just sourcing
`email` from the new `accounts.create` call instead of `pilots.create`.

### What Louis's "not invited by William" path looks like

Normal admin-issued (or bootstrap) referral code, `claimsPilotId` null - `pilots.create` runs
producing a **new**, separate `PilotId`, with its own new `account` row. His old unclaimed `Pilot`
row is untouched, still has no account. Acknowledged, expected outcome - a future merge capability
(explicitly out of scope) would reconcile the two later.

### 4. Deleting an account (not a pilot)

`DELETE /pilot/{pilotId}` (self-service) and `DELETE /admin/pilot/{pilotId}` (admin) both delete the
`Account` row and every `AuthIdentity` row for that pilot - never the `Pilot` row. This reverts the
`Pilot` to unclaimed (indistinguishable from one that was never registered), so its logged flight
history stays attached to the same `PilotId`, and an admin (or whoever originally created it, if
`created_by` is set) can invite it to be claimed again later exactly like any other unclaimed pilot.

Deleting the `AuthIdentity` rows too, not just the `Account` row, matters: `Auth.login` only checks
`accounts.isDisabled(...)` *after* a successful `AuthIdentity` lookup succeeds. Leaving a `PASSWORD`
identity behind with no matching `Account` would mean `isDisabled` finds no account row at all (not
"account found and disabled"), silently letting a stale password hash keep authenticating.

Same known pre-existing gap as disable: an already-issued session isn't invalidated by this (checked
only at login, not per-request by `SessionAuthFilter`) - not fixed here, not created by this change
either.

### Auth/login changes

`Auth.login`: `accounts.isDisabled(pilot.getId())` replaces `pilots.isDisabled(...)`. No special-case
needed for an accountless pilot attempting to log in - login is keyed off `AuthIdentity` lookup in
the first place, so a `Pilot` with no account simply has no matching identity and fails lookup before
the disabled check would even run.

### Admin endpoints (all need repointing to `Account`)

- `AdminEndpoint.listPilots` / `PilotRepository.findAllActivePage`: add a `LEFT JOIN account`
  alongside the existing `LEFT JOIN auth_identity` (same pattern already used for
  `signedUpAt`/`lastLoginAt`) - `email`/`disabled` become nullable in `PilotDto` for unclaimed
  pilots, so they show up in the admin list too rather than being hidden. `PilotDto.disabled` needs
  to become a nullable `Boolean` (currently primitive `boolean`).
- `AdminEndpoint.updatePilot` (enable/disable route) → `accounts.enable/disable(pilotId)`; 400/404 if
  the target pilot has no account (can't enable/disable something that doesn't exist).
- `AdminEndpoint.sendPasswordReset` → needs `accounts.get(pilotId)`'s email; 400/404 if no account.
- `AdminEndpoint.invitePilot`'s duplicate-email check → `accounts.findByEmail(email)` instead of
  `pilots.findByEmail`.
- `PilotEndpoint.updatePilot` (self-service `PUT /pilot/{pilotId}`) - same request/response DTO
  shape (no frontend impact), but internally splits into `Pilots.updateName(id, name)` +, if the
  email changed, `Accounts.updateEmail(id, email)` (which also fixes the `AuthIdentity.identifier`
  desync, see above).

## Migration - two-phase, per this repo's own compatibility rule

`CLAUDE.md`'s existing rule: a migration must never break the code currently running against it, and
the deploy pipeline runs `migrate` *before* the new app container replaces the old one - so briefly,
old code runs against the already-migrated schema. This migration only adds things (new table, new
nullable columns) and drops nothing, so the old code (which only ever reads/writes
`pilot.email`/`disabled_at` directly) keeps working completely unaffected during that window.

**`V3__pilot_account_split.sql` (this plan's migration - expand only):**
```sql
CREATE TABLE account (
    pilot_id     UUID PRIMARY KEY REFERENCES pilot(id),
    email        VARCHAR(255) NOT NULL,
    disabled_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT account_email_unique UNIQUE (email)
);

-- Backfill: every pilot with an auth_identity row today is, today, exactly "has an account."
INSERT INTO account (pilot_id, email, disabled_at)
SELECT p.id, p.email, p.disabled_at
FROM pilot p
WHERE p.email IS NOT NULL
  AND EXISTS (SELECT 1 FROM auth_identity ai WHERE ai.pilot_id = p.id);

ALTER TABLE pilot ADD COLUMN created_by UUID REFERENCES pilot(id);
ALTER TABLE referral_code ADD COLUMN claims_pilot_id UUID REFERENCES pilot(id);
```

`pilot.email`/`pilot.disabled_at` are **left in place, untouched, for this migration** - the new code
in this same deploy stops reading/writing them entirely (switches to `account`), but the columns
themselves aren't dropped yet.

**Follow-up:** originally planned as a later, separate migration dropping `pilot.email`/
`pilot.disabled_at` once a full deploy cycle confirmed nothing reads them (the repo's own
contract-phase rule). In practice there's no real pilot/flight data deployed yet, so there's no live
window to protect - `V4__drop_unused_pilot_columns.sql` drops both columns directly, in the same PR
as the expand phase, rather than waiting.

## New/changed endpoints summary

| Method | Path | Change |
|---|---|---|
| `POST` | `/pilot` | **New.** Authenticated, self-service. `{name}` → 201 `{id, name}`. Creates an unclaimed pilot, `created_by` = caller. |
| `POST` | `/pilot/{pilotId}/invite` | **New.** Authenticated. `{email}` → 201. 403 unless caller created `pilotId` or is admin. |
| `POST` | `/auth/register` | **Changed behavior, same request shape.** Email now goes through `accounts.create` instead of `pilots.create`. If the resolved referral code has `claimsPilotId`, attaches to that existing `Pilot` (and updates its name) instead of creating a new one. |
| `GET` | `/admin/pilots` | **Response shape change.** `email`/`disabled` become nullable (unclaimed pilots now appear in the list with both null, instead of not existing at all). |
| `PATCH` | `/admin/pilot/{pilotId}` | Same shape; now 400/404 if the target has no account. |
| `POST` | `/admin/pilot/{pilotId}/password-reset` | Same shape; now 400/404 if the target has no account. |
| `DELETE` | `/pilot/{pilotId}` | **Changed behavior, same shape.** Deletes the caller's `Account`/`AuthIdentity`, not the `Pilot` - see Design section 4. |
| `DELETE` | `/admin/pilot/{pilotId}` | **Changed behavior, same shape.** Deletes the target's `Account`/`AuthIdentity`, not the `Pilot`; now 404 if the target has no account. |

No change to `RegisterDto`, `LoginDto`, or any password-reset DTO - `hobbs-ui` needs zero changes for
this plan.

## Testing checklist for the implementing session

- `PilotsTest`: `create(name, createdBy)` with and without a creator.
- New `AccountsTest`/`AccountRepositoryTest`: `create`/`updateEmail` (asserting it also updates
  `AuthIdentity.identifier`)/`delete` (asserting it also deletes the `AuthIdentity` rows, and frees
  the email for reuse)/`disable`/`enable`/`isDisabled`/`findByEmail`, including the duplicate-email
  case (two different pilots, `account_email_unique` violation).
- `AuthTest`: registering via a `claimsPilotId`-scoped code attaches to the existing pilot and
  updates its name; existing non-claiming register/bootstrap/disabled-login tests still pass
  unchanged (just re-point their setup to create the account row via the new path).
- New endpoint tests for `POST /pilot` and `POST /pilot/{pilotId}/invite`, including the 403
  authorization case.
- `HobbsApplicationIntegrationTest`'s existing admin-list/enable-disable tests - update for the new
  nullable `email`/`disabled` shape, add a case asserting an unclaimed pilot shows up in the list
  with both null.
- `ReferralCodeRepositoryTest`: `claimsPilotId` persists/reads, including the null case unaffected.
- Integration test asserting deleting an account preserves the `Pilot`'s logged flight history under
  the same `PilotId`, and that pilot can be re-invited afterwards.
- Full `./gradlew test` run to confirm the migration and jOOQ codegen changes don't break anything.

## Explicitly out of scope (confirmed with Andy)

- `FlightEntry`/`SimulatorSession`/`FlightTrack` referencing a co-pilot's `PilotId` - a separate,
  later plan's decision entirely; this plan doesn't add or presume any particular shape for it.
- Any merge-two-pilot-records capability - future work, not designed here.
- Any `hobbs-ui` UI - nothing to show until a later plan adds a place to invite/pick a co-pilot.
- Fixing `SessionAuthFilter` not re-checking disabled status on every request (only at login) - a
  pre-existing gap, noted for awareness, not created or fixed by this plan.
