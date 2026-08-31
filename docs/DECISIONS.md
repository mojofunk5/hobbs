# Decisions

A dated record of significant architecture/engineering decisions for `hobbs` and its sibling repos
(`hobbs-ui`, `caddy`) where the decision affects this backend. Same convention as
`things/docs/BACKLOG.md`: **never delete an entry.** A decision that's later superseded gets a note
here pointing to what replaced it and why, rather than being removed - this file is a record of how
the system got the way it is, not a live TODO list (`docs/DECISIONS.md`'s sibling for outstanding
work is this repo's README "Not yet built" section and CLAUDE.md "Open work").

Reverse-chronological - newest first.

## 2026-08-31: add `GET /flight-entry-context`, a single-call prefetch for starting a new entry

Full plan: [`docs/plans/done/new-entry-context-endpoint.md`](plans/done/new-entry-context-endpoint.md).
`docs/plans/done/picker-recent-endpoints.md` (below) made `hobbs-ui`'s pickers lazy - each fetches its own
suggestions on focus. That's right for a screen a pilot might only partly use, but wrong for
create-flight-entry: `aircraftId`/`departureAirfieldId`/`arrivalAirfieldId`/`pilotInCommandId` are
all required on `FlightEntry`, so a pilot creating an entry is essentially certain to focus every
picker - lazy loading there just adds a sequential per-field round trip on a poor connection instead
of one round trip for the whole screen. `GET /flight-entry-context` is pure aggregation (a new
`FlightEntryContextEndpoint`/`FlightEntryContextDto`, no new domain logic) combining the existing
`Logbook.recentAirfields`/`recentAircraft` and `Pilots.searchKnownTo(callerId, null)` into one
response. The three individual endpoints stay - still needed for a picker re-fetching after being
cleared and refocused, and deliberately not extended to a future edit/amend screen (not yet built),
where "every required picker gets focused" doesn't obviously hold. The `hobbs-ui` wiring (pickers
consuming a prefetched batch instead of each fetching on focus) is its own separate doc + PR.

## 2026-08-31: add `GET /airfield/recent` and `GET /aircraft/recent` rather than reuse the search endpoints

Full plan: [`docs/plans/done/picker-recent-endpoints.md`](plans/done/picker-recent-endpoints.md). Prompted by a
`hobbs-ui` bug investigation: `AirfieldPicker` was fetching the entire ~1,200-row `airfield` table on
every focus (`GET /airfield` with no `search`) purely to surface the calling pilot's own last 5
flown airfields via `Logbook.searchAirfields`'s existing recent-first splice. Rather than have
`hobbs-ui` keep doing that just because the data happened to already be reachable that way, added two
endpoints sized to what's actually needed: `GET /airfield/recent` and `GET /aircraft/recent`, both
capped at 5, both backed by existing (airfield) or newly-added (aircraft, a new
`FlightEntryRepository#findRecentAircraftIds`) recent-flown lookups. `GET /airfield?search=` and
`GET /aircraft?search=` are unchanged - this is additive, not a breaking change to either.

Deliberately **not** extended to `GET /pilot?search=` - that endpoint's empty-query response is
already privacy-scoped to a small per-pilot set (people the caller has flown with), not a reference
table, so there's no analogous waste to fix there.

## 2026-08-30: Admin stays its own endpoint/`/admin/*` namespace, not folded into resource endpoints

Prompted by finishing the integration-test split (`docs/plans/done/split-integration-test-by-endpoint.md`)
and Andy asking whether admin capability should instead live on the relevant resource endpoint (e.g.
pilot disable/enable as a variant of `PUT /pilot/{id}` rather than its own `PUT
/admin/pilot/{id}`) - confirmed keeping the current shape rather than folding it in:

- `SessionAuthFilter` gates every admin operation with one uniform check (`path.startsWith("/admin/")`)
  - folding admin ops into `PilotEndpoint`/etc. would replace that with per-route "if admin, do X"
    branching inside each handler, which is both easier to get wrong (miss a branch, leave an authz
    hole) and harder to audit (can't tell what's admin-gated by reading route paths alone).
- Several pairs that look like "same operation, different permission" aren't: `PUT /pilot/{id}` is a
  self-service rename (`CreatePilotDto`), `PUT /admin/pilot/{id}` disables/enables
  (`UpdatePilotAdminDto`) - different request shapes and different domain effects on the same
  resource, so folding wouldn't even collapse much duplicate code.
- Matches `CLAUDE.md`'s already-declared Package Layout (six named `endpoint/` classes) and now
  matches the test split 1:1.

**Watch item, not yet acted on:** `AdminEndpoint.java` (296 lines) and
`AdminEndpointIntegrationTest` (34 tests) are already the largest of the six endpoint/test pairs -
the same shape (one class absorbing all of a subsystem's growth) that prompted the original
integration-test split. Not large enough yet to justify splitting further (e.g. invites vs. pilot
management as separate concerns), but worth revisiting if it keeps growing rather than letting it
repeat unnoticed the way the original 1047-line `HobbsApplicationIntegrationTest` did.

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
[`docs/plans/done/pilot-account-split.md`](plans/done/pilot-account-split.md) - including why `email`/`disabled`
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

## 2026-08-30: Aircraft picker - open questions resolved during implementation

[`docs/plans/done/aircraft-picker.md`](plans/done/aircraft-picker.md) left two things for review before
implementation; resolved as follows rather than blocking on a second doc round-trip:

- **DTO shape for browse vs. picker:** one expanded `AircraftDto` (all reference fields, most
  nullable), returned by the single `GET /aircraft?search=` endpoint and reused by both the picker
  (which only renders `id`/`registration`/`make`/`model`) and the Browse Aircraft page (which
  renders everything). Two DTOs (and two endpoints) would just be the same query shaped twice for
  no real behavioural difference - the picker doesn't pay a meaningful cost for the response
  carrying fields it ignores.
- **Index on `aircraft.registration`/`make`/`model`:** `registration` already had a unique-constraint
  index; added plain btree indexes on `make`/`model` in `V7__aircraft_reference_data.sql`. Deliberately
  **not** reaching for a substring-search index (Postgres `pg_trgm` or similar) yet - a btree index
  doesn't actually speed up the endpoint's arbitrary-substring `LIKE '%x%'` match, and there's no
  production-scale data yet to confirm the substring scan is actually slow. Revisit if/when
  `GET /aircraft?search=` is observed to be slow against the real ~600k-row imported table - same
  "don't build ahead of a confirmed need" reasoning as the rest of this repo's practice.

## 2026-08-30: Aircraft reference columns widened to TEXT after real-data crashes

The fixture CSV used in `AircraftImportJobTest` had make/model on every row and short values for
everything else, so V7's guessed `VARCHAR(n)` widths all looked reasonable in tests. The real
OpenSky CSV (94.5MB, ~520k rows) broke that three times in a row against production: `built`
crashing on a NULL `make`/`model` (V8), `manufacturer_icao VARCHAR(10)` too narrow for a real value
(V9, which also widened the other OpenSky-sourced columns pre-emptively), then `make VARCHAR(100)`
too narrow for one row that's a 120-character joined list of surnames - clearly a data-entry error
from an OpenSky contributor, not a real manufacturer name (V10, which also had to drop
`aircraft_make_idx` first - H2, used for jOOQ codegen and the test suite, refuses to index a
CLOB/TEXT column at all).

Downloaded the real CSV locally afterwards (see `docs/plans/done/aircraft-picker.md`'s data source URL)
and ran it end-to-end against a local H2 database to sanity-check the rest, rather than continuing
to whack-a-mole one crash per production attempt. Found two more real issues, fixed proactively:

- `built` is a full ISO date (`"1978-01-01"`) rather than a bare year on ~49% of rows -
  `AircraftImportJob.parseYear` only handled a plain integer, silently discarding the year on
  almost half the dataset. Fixed to extract the leading 4 digits either way.
- 504 rows share the placeholder registration `"SERV"` (clearly not a real, distinct tail number).
  Left as-is: `upsertByRegistration`'s last-one-wins behavior means these collapse into a single
  row, which is an acceptable cost given OpenSky's own "no support or guarantees" disclaimer on
  this dataset - not a bug in this repo's code, and not worth special-casing a specific junk value.

Full real-CSV import (520,000 rows, 3,473 skipped for no registration) now completes cleanly with
V7-V10 all applied.

## 2026-08-30: Airfield picker chunk 4 - expand-only, no backfill, no contract yet

[`docs/plans/done/airfield-picker.md`](plans/done/airfield-picker.md)'s Open questions flagged the
`FlightEntry.departurePlace`/`arrivalPlace` -> `AirfieldId` migration as needing "its own
expand/backfill/contract sequence" and left scoping it to implementation. Implemented as expand
only, deliberately stopping there:

- **Expand (V12__flight_entry_airfield_id_expand.sql):** added nullable
  `departure_airfield_id`/`arrival_airfield_id` UUID columns referencing `airfield(id)`, alongside
  the existing `departure_place`/`arrival_place` free-text columns - those are untouched. `FlightEntry`,
  `FlightEntryRepository`, `CreateFlightEntryDto`/`FlightEntryDto`/`FlightEntryMapper`, and
  `FlightEntryEndpoint` all now accept/expose optional `departureAirfieldId`/`arrivalAirfieldId`
  alongside the still-required free-text fields.
- **No backfill, and none planned.** There is no reliable way to match an existing row's free-text
  place string (e.g. `"EGCM"`, `"Sherburn"`) to a specific `airfield.name`/`icao_code` without
  fuzzy string-matching, which the plan doc's own reasoning (and the standing "reference by id, not
  text" convention) explicitly argues against. Existing rows simply keep both
  new id columns `NULL` indefinitely - this is a deliberate, permanent state for pre-migration rows,
  not a TODO to revisit with a smarter matching algorithm later. Consistent with CLAUDE.md's
  migration-safety rule: the *previous* release's code (which only ever wrote the free-text columns)
  keeps producing correct rows against this new schema, since the new columns are nullable and never
  required.
- **No contract step**, and none scheduled. Dropping `departure_place`/`arrival_place` (or making
  the id columns `NOT NULL`) is itself a breaking schema/API change - every existing `FlightEntry`
  row lacking an id would need a real answer (either an admin backfill exercise, or accepting some
  rows permanently keep only free-text) before that could safely happen. Tracked as a follow-up in
  CLAUDE.md's Open work, not designed or scheduled here - a future decision, not an oversight in this
  chunk.

Chunk 5 (recent-airfields ranking on `GET /airfield?search=`) builds on these new nullable columns
directly - it only ever reads flight entries that do have an id set (new entries going forward),
which is consistent with "recently flown" naturally excluding entries with no id yet.

## 2026-08-30: Airfield picker chunk 6 - contract step done after all, supersedes the "no contract scheduled" call above

The chunk 4 entry above (and `docs/plans/done/airfield-picker.md`'s Open questions) deliberately left no
contract step scheduled, because there was no safe way to backfill `departureAirfieldId`/
`arrivalAirfieldId` onto existing `FlightEntry` rows that only ever had free-text `departurePlace`/
`arrivalPlace` - dropping the free-text columns, or making the id columns `NOT NULL`, would have
stranded those rows with no id and no way to derive one.

That reasoning no longer applies, and the plan is superseded rather than followed: **zero real
`FlightEntry` rows exist in production** - confirmed directly by Andy - the app is live
(`hobbs.bssd.co.uk`) but nobody has logged a real flight through it yet. With no real data to lose or
migrate, Andy explicitly decided (2026-08-30, in conversation, not a doc PR - the decision itself is
the record) to skip the staged expand/backfill/contract sequence and go straight to the contract now,
rather than carry the dual-field state indefinitely waiting for a hypothetical future backfill that
was never going to be possible anyway.

Implemented in `V13__flight_entry_airfield_id_contract.sql`: `departure_airfield_id`/
`arrival_airfield_id` become `NOT NULL`, `departure_place`/`arrival_place` are dropped entirely.
`FlightEntry`, `FlightEntryRepository`, `CreateFlightEntryDto`/`FlightEntryDto`/`FlightEntryMapper`,
and `FlightEntryEndpoint` all now require `departureAirfieldId`/`arrivalAirfieldId` - the free-text
fields and all handling for their absence are gone, not just deprecated.

This is not a blanket exemption from CLAUDE.md's migration-safety rule ("a migration must never break
the code currently running against it") - see the migration file's own comment for the residual risk
this still carries (a partially-failed deploy leaving pre-this-PR code running against the new
schema) and why it's an accepted risk here specifically: this migration ships in lockstep with a
matching `hobbs-ui` release (`feature/airfield-picker-6-ui`, a sibling PR in that repo) that already
sends the new required fields and no longer sends the old ones, so migration and code move together -
the same assumption the deploy pipeline's `migrate` -> `docker compose up -d app` sequencing already
makes for every other migration in this repo, not a new exception invented for this one.

## 2026-08-30: Aircraft/pilot/airfield picker search performance - revisiting the deferred index question

Andy reported the pickers (aircraft/pilot/airfield, all built on the same typeahead pattern in
`hobbs-ui`) feel "clunky" and "half the time don't look like they are doing anything." Investigated
both halves of that separately, since they're independent causes with independent fixes:

- **Backend query speed:** confirmed the exact gap the 2026-08-30 "Aircraft picker" entry above
  flagged as a revisit trigger. `pilot.name` has no index at all; `aircraft`/`airfield` have plain
  btree indexes on the searched columns, which (as that entry already predicted) don't speed up an
  arbitrary-substring `LIKE '%x%'` match - only a prefix match. At `pilot`/`airfield`'s real scale
  (a handful of rows today, ~1,200 once airfields are imported) this genuinely doesn't matter - even
  a full table scan is fast. At `aircraft`'s real scale (~600k rows, the full OpenSky import already
  ran), it's the actual bottleneck: this is the "revisit if/when observed to be slow" trigger that
  entry left open, so `V14__aircraft_search_trigram_indexes.java` adds Postgres `pg_trgm` GIN
  indexes on `lower(registration)`/`lower(make)`/`lower(model)` (matching the exact expression
  `AircraftRepository`'s queries filter on - see that migration's own Javadoc for why a plain-column
  index wouldn't get used). Deliberately scoped to aircraft only, not pilot/airfield too - indexing a
  table that's already fast wouldn't fix anything a user could perceive, and would just be
  unexplained complexity sitting next to two tables where it does nothing.
- **Perceived responsiveness:** independent of query speed, the picker widgets themselves have a
  window with no visual feedback at all - the debounce timer runs for 300ms after every keystroke
  before a search even starts, and only then does the "searching" spinner appear. That's a UI-only
  fix, tracked as its own PR in `hobbs-ui`, not part of this backend change.

These are shipped as two separate PRs (one per repo) rather than bundled, since they're independent
root causes with independent, unrelated fixes - a slow query and a missing loading indicator don't
belong in the same reviewable change just because a user experienced them as one symptom.
