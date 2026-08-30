# Plan: Aircraft reference data & picker

**Status:** Designed 2026-08-30, not yet implemented.

## Context

Story 2 of 3 split out of the "pasted-in ids when adding a flight entry" backlog item (see
`CLAUDE.md`'s Open work) - the pilot half is done ([`docs/plans/pilot-picker.md`](pilot-picker.md)),
location is a separate, bigger plan (needs a domain decision first).

Today `aircraftId` on `POST /flight` takes a raw `AircraftId` the caller already has to know.
Aircraft themselves are self-registered: `POST /aircraft` lets any pilot type in a registration,
make, model and `EngineCategory` (`SINGLE_ENGINE`/`MULTI_ENGINE`), and `GET /aircraft` returns every
aircraft in the system with no search (see `AircraftEndpoint.java`). Unlike the pilot picker, this
plan is not "add search to an existing pilot-driven flow" - Andy wants to change where aircraft data
comes from entirely: seeded from a public registry instead of typed in by hand, with no "add
aircraft" left in the pilot-facing picker at all.

## Data source: OpenSky aircraft database

Checked what's actually available before designing against it, rather than assuming:

- **UK CAA G-INFO** (the official UK register) is search-only for free; the bulk/programmatic export
  is a **paid**, monthly Excel file with a single-PC license - not something to build an automated
  import against.
- **OpenSky Network's aircraft database** is free, unauthenticated, and global (not UK-only):

  ```
  https://opensky-network.org/datasets/metadata/aircraftDatabase.csv
  ```

  (302-redirects to an S3 bucket; ~94.5 MB, ~600k+ rows.) Columns include `icao24`, `registration`,
  `manufacturericao`, `manufacturername`, `model`, `typecode`, `serialnumber`, `icaoaircrafttype`
  (encodes engine count/type, e.g. `L2P` = land, 2 engines, piston), `operator`, `owner`, `built`,
  `engines` (free text), `categoryDescription`. Aggregated from official registries, Basestation
  files, openflights.org, ICAO Doc 8643, and crowdsourced contributions. Explicitly **"unlicensed...
  offered as is, no support or guarantees."**

- **Important constraint on the "automate a regular pull" ask:** the file's `Last-Modified` is
  November 2024, and OpenSky's own site says outright: *"The crowdsourced aircraft database may be
  made available again at a further date... monthly snapshots are also available but updates are
  currently on hold."* There is nothing new to pull right now - the upstream feed itself is paused,
  not just infrequently checked. See Confirmed decisions below for how this changes the shape of
  "automation" here.

## Confirmed decisions

- **`Aircraft` becomes reference data, not pilot-submitted.** `POST /aircraft` (self-service
  registration) is removed. Aircraft only enter the system via the CSV import/reconciliation job
  below.
- **No pilot-facing "unknown aircraft" / request-to-add flow in the picker.** The picker only ever
  searches existing reference data - it has no "can't find it? add it" affordance. Gaps in the seed
  data are explicitly not handled by this plan at all - see Out of scope below for the direction a
  later story would take.
- **Import/reconciliation is a re-runnable job, not a tightly-scheduled one.** Modelled as a new CLI
  subcommand alongside `migrate` (e.g. `HobbsApplication import-aircraft`), idempotent upsert by
  `registration` (case-insensitive exact match - the natural key both systems agree on):
  - CSV row with a registration not yet in `aircraft` -> insert.
  - CSV row matching an existing `aircraft.registration` -> update the reference fields.
  - Existing `aircraft` row with no matching CSV row -> **left alone, never deleted** - `FlightEntry`
    rows can reference it via `AircraftId`, and the migration-safety rule in `CLAUDE.md` (never break
    currently-running/persisted data) applies here too even though this isn't a schema migration.
  - Because the upstream file itself isn't currently being refreshed, there's nothing to gain from
    wiring up a tight automated schedule (e.g. a nightly cron) yet - the job is built to be triggered
    on demand (manually, or from a coarse periodic timer on the VPS outside the app's own deploy
    pipeline) and re-run safely whenever OpenSky does resume snapshots. Automating the trigger itself
    is cheap to add later; it's just not useful today against a frozen source.
- **Existing `aircraft` table gets expanded, not replaced** - `registration`, `make` (from
  `manufacturername`), `model` stay; new nullable columns added for `manufacturerIcao`, `typeCode`,
  `serialNumber`, `operator`, `owner`, `built` (year), `engines` (free text), `categoryDescription`.
  All nullable since rows created before this plan (and any CSV rows missing a field) won't have
  them. `EngineCategory` (`SINGLE_ENGINE`/`MULTI_ENGINE`) derived from `icaoaircrafttype`'s engine-count
  digit where parseable, left `null` otherwise rather than guessed.
- **Import the full global dataset** - all ~600k rows, not filtered to UK. Confirmed: gives William
  (and any future pilot) searchability for aircraft flown anywhere, not just G-registered ones.
  This means, unlike the pilot picker, the aircraft table is genuinely large - see the search/browse
  endpoint behaviour below, which is shaped around that from the start rather than "no pagination,
  revisit if that changes."
- **`GET /aircraft?search=` requires the caller to actually type something - no "return everything"
  default.** Unlike the pilot picker (small, per-caller "known to" set, so an empty search
  reasonably returns the full set for an initial dropdown), an empty or missing `search` here would
  mean "all ~600k aircraft" - not a sane response. Confirmed shape:
  - `search` is **required**, minimum 2 characters - a blank/1-character query returns `400`, not an
    empty or unbounded list.
  - Substring match across registration/manufacturer/model, case-insensitive, same as before.
  - Results capped (e.g. top 50, ordered by registration) - even a specific-enough typed substring
    could match more than a picker dropdown should render.
  - This is a genuine behavioural difference from the pilot picker's `GET /pilot?search=`, worth
    calling out in the OpenAPI description so it isn't mistaken for the same contract.
  - Replaces the current unfiltered `GET /aircraft` as the picker's backing endpoint.
- **Browse Aircraft page** (`hobbs-ui`): same "must search" shape applies - not a page that lists
  600k rows with pagination controls, but a search-first view (registration/manufacturer/model)
  that only renders results once the pilot's typed enough to narrow it down, surfacing whatever
  import fields are populated (owner, built year, engines, operator, serial number) - not just the
  picker's minimal fields. New nav entry, "Browse Aircraft".
- **`POST /aircraft` (self-service, manual-entry registration) is removed outright, no replacement.**
  Not worth designing a gap-filling mechanism ahead of ever needing one - see Out of scope for the
  direction that'd take if/when a real gap shows up.

## Open questions (for review on this doc, before implementation)

- **DTO shape for browse vs. picker.** The picker (typeahead while adding a flight entry) probably
  wants the same minimal `id`/`registration`/`make`/`model` shape it has today. The browse page wants
  more (owner, built, engines, operator). One richer `AircraftDto` reused by both, or two DTOs? Same
  kind of question the pilot-picker plan resolved by reusing `PilotSummaryDto` - but that endpoint
  didn't also need to feed a full browse page, so it's not a direct precedent here.
- **Index needed on `aircraft.registration`/`manufacturername`/`model`** for the substring search to
  stay fast against ~600k rows - not a design question so much as a reminder for the migration
  chunk; H2 in tests won't surface a missing-index problem the way production Postgres-scale data
  eventually would.

## Chunking

Per `CLAUDE.md`'s "keep PRs small" rule:

1. **Migration** - additive, nullable columns on `aircraft` (see Design above). No behaviour change.
2. **Import/reconciliation job** - CSV parsing, upsert-by-registration logic, new CLI subcommand,
   tests against a fixture CSV (not a live network call in tests).
3. **`GET /aircraft?search=` endpoint** + OpenAPI doc; remove `POST /aircraft` (and its now-dead
   `CreateAircraftDto`/tests).
4. **`hobbs-ui`** - picker widget wiring (same shape as the pilot picker), Browse Aircraft page + nav
   entry.

## Explicitly out of scope (left for later)

- **Gap-filling for aircraft missing from the OpenSky seed** - not designed as part of this story;
  explicitly a later one, scoped here only so the direction is written down rather than lost:
  - No pilot-facing "request to add" flow - handled by an admin instead, same reasoning as removing
    `POST /aircraft` in the first place (authoritative data in, not hand-typed).
  - The practical mechanism checked and likely to use when this is built: an admin-triggered,
    single-registration lookup (e.g. `POST /admin/aircraft/lookup/{registration}`) against
    **AeroDataBox** (via RapidAPI) - it has a direct aircraft-by-registration endpoint, and its free
    tier (600 API units / 2,400 requests per month, 1 req/sec) comfortably covers occasional
    one-at-a-time lookups, unlike AviationStack's free tier (100 requests/month, too thin even for
    this). Feeds the same upsert-by-registration path the bulk import uses. Needs a RapidAPI API key
    provisioned as app config when it's actually built - the first external credential this plan
    would introduce beyond OpenSky's unauthenticated CSV.
  - Deliberately not building this now: no gap has actually been hit yet (small, known set of
    aircraft), and it's a genuinely separate concern (external API integration, its own credential,
    its own error handling for "AeroDataBox doesn't have it either") from the bulk-CSV work in this
    plan - exactly the kind of premature scope this repo's PR-sizing practice says to leave for a
    real follow-up rather than build speculatively.
- **Location picker** - separate plan, needs a domain decision (self-owned `Location`/`Airfield`
  table vs. live maps dependency) before it can even be scoped, per `CLAUDE.md` Open work.
- **Re-enabling automatic OpenSky snapshot pulls on a schedule** - not useful while the upstream
  crowdsourced database itself is on hold; revisit once OpenSky resumes publishing snapshots.
