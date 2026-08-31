# Hobbs

An electronic PPL flight logbook. Log flights the old-fashioned way (a form matching the UK CAA/EASA
standard logbook - CAP804 / FCL.050) or hit record before you fly and let GPS pre-fill the entry for
you to confirm afterwards. GPS recording is always optional - a manually-entered flight is just as
valid as one derived from a recorded track, and a track that cuts out partway through still pre-fills
whatever it captured.

Not a replacement for your paper logbook - the UK CAA still requires that as the primary record. This
is a second, self-hosted copy that's much harder to lose.

## Running locally

Schema migrations are a separate, explicit step from starting the server, not an implicit side effect
of booting - a routine restart with zero schema changes shouldn't re-run Flyway, and a new migration
shouldn't land silently at whatever moment the app happens to restart, with no chance to review,
sequence, or roll it back separately from the code deploy. Run `migrate` once before the first run
against a fresh database, and again any time a new migration is added:

```bash
./gradlew shadowJar
java -jar build/libs/hobbs-0.0.1-SNAPSHOT-all.jar migrate
java -jar build/libs/hobbs-0.0.1-SNAPSHOT-all.jar 8080
```

By default the app creates an H2 file database at `./hobbs.mv.db` in the working directory.

### Seeding aircraft reference data

`Aircraft` is reference data (see the model summary below), imported/reconciled from an
already-downloaded copy of [OpenSky's aircraftDatabase.csv](https://opensky-network.org/datasets/metadata/aircraftDatabase.csv)
rather than pulled live - not a server-boot side effect, same "explicit, deliberate step" reasoning
as `migrate`, and re-runnable/idempotent (upserts by registration, never deletes):

```bash
java -jar build/libs/hobbs-0.0.1-SNAPSHOT-all.jar import-aircraft /path/to/aircraftDatabase.csv
```

### Seeding airfield reference data

`Airfield` is reference data too (see the model summary below), imported/reconciled from an
already-downloaded copy of [OurAirports' airports.csv](https://davidmegginson.github.io/ourairports-data/airports.csv)
(filtered to the GB subset on import) rather than pulled live - same "explicit, deliberate step"
reasoning as `import-aircraft`, and re-runnable/idempotent (upserts by source id, never deletes):

```bash
java -jar build/libs/hobbs-0.0.1-SNAPSHOT-all.jar import-airfields /path/to/airports.csv
```

### Running via Docker Compose

```bash
cp .env.example .env
docker compose up -d postgres
docker compose run --rm --build app migrate
docker compose up -d app
```

Brings up Postgres and the app only, at `http://localhost:8080`. The frontend is
[`hobbs-ui`](https://github.com/mojofunk5/hobbs-ui) (Flutter web), live at
[hobbs.bssd.co.uk](https://hobbs.bssd.co.uk) - TLS/reverse-proxy for both is a separate shared
[`caddy`](https://github.com/mojofunk5/caddy) repo, not part of either app's own compose stack.

## Running tests

```bash
./gradlew test
./gradlew test jacocoTestReport
```

367 tests, 93% instruction / 82% branch coverage: full coverage of the auth/session/referral-code/
admin/password-reset subsystem (unit tests plus end-to-end integration tests, one class per endpoint
- `HealthEndpointIntegrationTest`, `AircraftEndpointIntegrationTest`, `FlightEntryEndpointIntegrationTest`,
`PilotEndpointIntegrationTest`, `AuthEndpointIntegrationTest`, `AdminEndpointIntegrationTest` - sharing
fixture setup via `AbstractIntegrationTest`; see
[`docs/plans/split-integration-test-by-endpoint.md`](docs/plans/split-integration-test-by-endpoint.md)),
covering register/login, admin/referral-code flows, and the 401/403/404 auth-boundary cases, plus
tests for the flight domain - `LogbookTest` (mocked repositories), `AircraftRepositoryTest`,
`FlightEntryRepositoryTest`, `FlightTrackRepositoryTest`, `SimulatorSessionRepositoryTest` (real H2
via Flyway), `AircraftImportJobTest` (against a checked-in fixture CSV, never a live network call),
value-object tests (`PilotTest`, `AircraftTest`, `FlightEntryTest`, `FlightTrackTest`,
`SimulatorSessionTest`), and `AppConfigTest`.

`RateLimitRepositoryTest` uses an injectable `Clock` on `RateLimitRepository` rather than the real
wall clock - the fixed-window rate limiter's window is "the current second", so asserting against
real time was intrinsically flaky whenever a second ticked over mid-test.

## Architecture

- **Layered, inside-out dependencies**: `endpoint` → `mapper` → `dto`, `endpoint` → `domain`. The
  domain has no knowledge of HTTP, JSON, or persistence.
- **Thin service objects, rich domain model**: `Logbook` and `Pilots` are orchestration shells wiring
  repositories to domain objects; behaviour lives in the domain classes themselves.
- **Microtypes for identifiers**: `PilotId`, `AircraftId`, `FlightEntryId`, `FlightTrackId`,
  `SimulatorSessionId` all extend `TypedId` rather than passing raw `UUID`s at domain boundaries.
- **REST over HTTP with OpenAPI documentation inline**, Javalin routes with `@OpenApi` annotations
  colocated with handlers.

### Auth

Session-based auth (`Bearer <sessionId>`), an admin bootstrap on first run (a one-time code logged to
the server console when no admin exists yet), and registration gated behind an admin-issued,
single-use, email-scoped referral code with its own TTL. Public repo, public app - but registration
is deliberately hard to come by, so that being open source doesn't mean being open to sign-ups.

## Domain model

Based on the UK CAA/EASA standard logbook format (CAP804 = FCL.050 template):

- **Pilot** - someone recordable on a flight (PIC, co-pilot, instructor); just `id`/`name`/who created
  it. Not the same as an account - see **Account** below
- **Account** - the login/email/enabled-state half of a `Pilot`, one-to-one with a `Pilot` via
  `pilot_id`. A `Pilot` has an account iff a matching `Account` row exists; a `Pilot` with none is an
  "unclaimed" record, e.g. a co-pilot logged before they'd signed up. See
  [`docs/plans/pilot-account-split.md`](docs/plans/pilot-account-split.md) for the full design.
- **Aircraft** - reference data seeded from OpenSky's aircraftDatabase.csv
  ([`docs/plans/aircraft-picker.md`](docs/plans/aircraft-picker.md)), not pilot-submitted - there is no
  `POST /aircraft`. registration/make/model plus engine category (derived from OpenSky's
  `icaoaircrafttype` where parseable, else `null`) and a handful of nullable OpenSky reference fields
  (manufacturer ICAO code, type code, serial number, operator, owner, built year, engines,
  category description). Shared across pilots, not scoped to one account.
  `GET /aircraft?search=` (required, minimum 2 characters, capped at 50 results) backs both the
  flight-entry aircraft picker and the Browse Aircraft page in `hobbs-ui`. `GET /aircraft/recent`
  returns the calling pilot's own last 5 distinct flown aircraft, most recently flown first - a
  right-sized on-focus browse for the picker (see
  [`docs/plans/picker-recent-endpoints.md`](docs/plans/picker-recent-endpoints.md)).
- **FlightEntry** - one row of the logbook: date, departure/arrival (each an `AirfieldId` + time -
  see **Airfield** below; no free-text place field), `pilotInCommandId`/`coPilotId` (both `PilotId`s -
  see `docs/GLOSSARY.md`'s **PIC** entry for how the PIC can differ from the entry's owner), and
  every duration (single/multi-engine, total, night, IFR, cross-country, PIC, co-pilot, dual,
  instructor) plus day/night landings and remarks. All durations are stored in whole minutes, not
  float hours, to avoid rounding drift across hundreds of entries. `flightTrackId` and `coPilotId`
  are nullable and optional - see below.
- **Airfield** - reference data seeded from OurAirports' GB dataset
  ([`docs/plans/airfield-picker.md`](docs/plans/airfield-picker.md)), not pilot-submitted - there is
  no `POST /airfield`. name/ICAO code/municipality/country/region/coordinates/elevation/type, shared
  across pilots. `GET /airfield?search=` (name substring or ICAO code prefix; empty search returns
  the full GB set alphabetically, no minimum-length restriction) backs the flight-entry departure/
  arrival picker, ranked with the calling pilot's own recently-flown airfields first. `GET
  /airfield/recent` returns just those last 5 distinct flown airfields, most recently flown first -
  a right-sized alternative for on-focus browsing (see
  [`docs/plans/picker-recent-endpoints.md`](docs/plans/picker-recent-endpoints.md)) rather than
  loading the full ~1,200-row table.
- **FlightTrack** - a raw GPS recording (points stored as a single JSON blob for now, not one row per
  point - see the class Javadoc for why). Feeds a *draft* `FlightEntry` that the pilot confirms or
  corrects; never writes a `FlightEntry` on its own.
- **SimulatorSession** - FSTD (simulator) time, its own row shape in the real logbook, kept separate
  from `FlightEntry`.

## Not yet built

- Deriving a draft `FlightEntry` from a `FlightTrack` (departure/arrival detection, landing counting,
  night-time-from-sunset-tables, cross-country distance) - currently `FlightEntry` and `FlightTrack`
  exist as separate persisted things with no automatic bridge between them yet
- Pagination/filtering on `GET /flight` (currently returns everything for the authenticated pilot)
- `SimulatorSession`/`FlightTrack` referencing a co-pilot's `PilotId` - `FlightEntry` now has
  `pilotInCommandId`/`coPilotId`, but the other two flight-domain classes don't reference a `PilotId`
  for a co-pilot yet
- Merging two `Pilot` records (e.g. someone who registered their own account instead of using an
  invite that would've attached them to an unclaimed record someone else already created)
- Pasted-in ids when adding a flight entry - `PilotId`/`AircraftId`/`AirfieldId` were entered as raw
  text originally (see [`docs/plans/logbook-entries.md`](docs/plans/logbook-entries.md)). Split into
  three stories in `CLAUDE.md`'s Open work, backend now done for all three - see
  [`docs/plans/pilot-picker.md`](docs/plans/pilot-picker.md),
  [`docs/plans/aircraft-picker.md`](docs/plans/aircraft-picker.md), and
  [`docs/plans/airfield-picker.md`](docs/plans/airfield-picker.md) - `hobbs-ui`'s picker widgets are
  the remaining piece for each
- Editing or deleting a flight entry - only creating, viewing, and listing are planned for now
- Logbook entry screens, photo-to-logbook OCR, GPS-recording-to-logbook, and the iOS app all live in
  [`hobbs-ui`](https://github.com/mojofunk5/hobbs-ui) - see that repo's `docs/architecture-brief.md`
  for the roadmap

See [`docs/GLOSSARY.md`](docs/GLOSSARY.md) for aviation and domain terms (PIC, dual, unclaimed pilot,
etc.), and `docs/DECISIONS.md` for a dated record of significant architecture/engineering decisions
made along the way.
