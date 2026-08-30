# Decisions

A dated record of significant architecture/engineering decisions for `hobbs` and its sibling repos
(`hobbs-ui`, `caddy`) where the decision affects this backend. Same convention as
`things/docs/BACKLOG.md`: **never delete an entry.** A decision that's later superseded gets a note
here pointing to what replaced it and why, rather than being removed - this file is a record of how
the system got the way it is, not a live TODO list (`docs/DECISIONS.md`'s sibling for outstanding
work is this repo's README "Not yet built" section and CLAUDE.md "Open work").

Reverse-chronological - newest first.

## 2026-08-30: CI made faster - full writeup in `docs/CI_PERFORMANCE.md`

Three changes, only two of which worked as designed the first time:

- **JUnit tests run classes and methods concurrently** (`src/test/resources/junit-platform.properties`).
  Correcting an over-conservative first pass that defaulted methods to `same_thread` and left
  `HobbsApplicationIntegrationTest` (83 of the suite's tests) fully serial regardless of core count -
  the actual bottleneck, not the risk the conservative default was guarding against. Surfaced a real,
  rare race once genuine concurrency was in play: `FailedAttemptRepository` had no injectable `Clock`,
  unlike `RateLimitRepository` - fixed with the same pattern. `./gradlew test`: ~54s -> ~25s locally.
- **Docker build caching didn't work, then did.** First attempt used
  `RUN --mount=type=cache,target=/root/.gradle` - looked right, made three consecutive real deploys
  slower (59s -> 96s -> 71s), not faster. Root cause: a cache *mount* is BuildKit-local state tied to
  the runner's own disk, never exported by `cache-to: type=gha` (that only exports image layers) -
  meaningless on GitHub Actions' ephemeral runners. Replaced with the standard package-manager Docker
  pattern instead (copy `build.gradle`/the wrapper first, resolve dependencies into their own layer,
  then copy the rest of the source) - genuine, exportable layer caching.
- **Docs-only commits skip the expensive `build` job**, not the whole workflow. A trigger-level
  `paths-ignore` looked like the obvious fix but is actively broken on a repo with a required status
  check (this repo has one - see the entry below on branch protection - `build`, same as `hobbs-ui`):
  a workflow that never triggers never posts any status, so GitHub leaves that required check stuck
  "waiting to be reported" forever and blocks merging. Fixed with an always-running `changes` job
  (via `dorny/paths-filter`) whose output conditionally skips the `build` job via `if:` - a job
  skipped this way still reports "skipped", which GitHub counts as passing. Same pattern in
  `hobbs-ui`, verified end-to-end there via a real docs-only PR reporting `MERGEABLE`.

## 2026-08-29: Pilot/account split implemented

`Pilot` ("a person recordable on a flight") and `Account` (login/email/enabled-state) were one table.
That meant a co-pilot who hadn't signed up couldn't be recorded by name - there was no way to create a
`Pilot` without also giving it credentials. Split into two tables (`pilot` stays minimal: `id`, `name`,
`created_by`; new `account` table holds `email`/`disabled_at`, one-to-one via `pilot_id`) so an
"unclaimed" `Pilot` can exist with no account, and the pilot who logged it can later invite the real
person to claim it via a referral code scoped to that specific `PilotId`
(`referral_code.claims_pilot_id`). Full design, ground truth, and rationale in
[`docs/plans/pilot-account-split.md`](plans/pilot-account-split.md) - including why `email`/`disabled`
were dropped from `Pilot` entirely rather than just relaxed, and the pre-existing
`pilot.email`/`auth_identity.identifier` desync bug fixed as a byproduct. The plan called for an
expand-only migration (leaving `pilot.email`/`disabled_at` in place until a later, separate migration,
per the repo's contract-phase rule for live deploys) - but since no real pilot/flight data is deployed
yet, there's no live-compatibility window to protect, so `V4__drop_unused_pilot_columns.sql` drops
both columns in this same PR instead of waiting. Merging two `Pilot` records (e.g. someone who
registered independently instead of via an invite) and referencing a co-pilot's `PilotId` from
`FlightEntry` are both explicitly out of scope, left for later plans.

**Correction, same day:** the first implementation pass left account deletion soft-deleting the
`Pilot` row itself, unchanged from before this split. Corrected: disable/enable/delete all act on
`Account`, never `Pilot` - a `Pilot`'s logged flight history must survive its account being deleted,
exactly as it already survives an account never having existed. `pilot.deleted_at` was dropped as a
result (`V5__drop_pilot_deleted_at.sql`, same no-real-data reasoning as above).

## 2026-08-29: Branch protection + auto-delete-on-merge added retroactively

Both `hobbs` and `hobbs-ui` are public GitHub repos, so real branch protection (require a PR, require
the `build` status check, block force-push/delete, applies even to admins) has been available for
free the whole time - just hadn't been turned on. Enabled once both repos' CI actually ran `build` on
every branch (not just `master` - see the `hobbs-ui` `chore/ci-run-on-all-branches` fix, since
requiring a status check that never runs on a PR branch would just block every merge). No required
approval count: Andy is the sole reviewer, so requiring an approval would just block him reviewing
his own PRs.

Auto-delete-on-merge was enabled alongside this. Doesn't conflict with the CLAUDE.md rule "never
delete a branch" above - that rule is about actions Claude takes autonomously; a branch deleted as a
direct, visible consequence of Andy clicking "Merge" is his own action, not an autonomous deletion.

## 2026-08-29: Caddy consolidated into a separate shared `caddy` repo

`hobbs-ui` originally shipped with its own per-repo Caddy compose stack (mirroring the pattern
`things-ui` used at the time). That broke the moment `hobbs-ui` needed the same host ports 80/443
that `things-ui`'s own Caddy container already had bound - two independent Caddy containers can't
both bind the same host ports on one VPS. Rather than pick non-standard ports for one of them (or
inventing a co-existence hack), consolidated into a single shared [`caddy`](https://github.com/mojofunk5/caddy)
repo: one Caddy instance, one Caddyfile with a site block per domain
(`things.bssd.co.uk`/`hobbs.bssd.co.uk`), each app's own repo just deploys static files/proxies to
its own backend container by name. Neither `hobbs` nor `hobbs-ui` runs its own reverse proxy or holds
a TLS cert anymore - see the CLAUDE.md note above.
