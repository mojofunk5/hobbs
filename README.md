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

248 tests, 94% instruction / 84% branch coverage: full coverage of the auth/session/referral-code/
admin/password-reset subsystem (unit tests plus an end-to-end `HobbsApplicationIntegrationTest`
covering register/login, admin/referral-code flows, and the 401/403/404 auth-boundary cases), plus
tests for the flight domain - `LogbookTest` (mocked repositories), `AircraftRepositoryTest`,
`FlightEntryRepositoryTest`, `FlightTrackRepositoryTest`, `SimulatorSessionRepositoryTest` (real H2
via Flyway), value-object tests (`PilotTest`, `AircraftTest`, `FlightEntryTest`, `FlightTrackTest`,
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
- **Aircraft** - registration, make, model, engine category. Shared across pilots, not scoped to one
  account (a club trainer only needs registering once)
- **FlightEntry** - one row of the logbook: date, departure/arrival (place + time),
  `pilotInCommandId`/`coPilotId` (both `PilotId`s - see `docs/GLOSSARY.md`'s **PIC** entry for how
  the PIC can differ from the entry's owner), and every duration (single/multi-engine, total, night,
  IFR, cross-country, PIC, co-pilot, dual, instructor) plus day/night landings and remarks. All
  durations are stored in whole minutes, not float hours, to avoid rounding drift across hundreds of
  entries. `flightTrackId` and `coPilotId` are nullable and optional - see below.
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
- Pilot/aircraft search or pickers when adding a flight entry - `PilotId`/`AircraftId` are entered
  as a pasted-in id for now (see [`docs/plans/logbook-entries.md`](docs/plans/logbook-entries.md)).
  Fine while there's one real user's worth of test data; doesn't scale past that, and there's no
  search-by-name endpoint yet to build the picker against (`GET /admin/pilots` is admin-only)
- Editing or deleting a flight entry - only creating, viewing, and listing are planned for now
- Logbook entry screens, photo-to-logbook OCR, GPS-recording-to-logbook, and the iOS app all live in
  [`hobbs-ui`](https://github.com/mojofunk5/hobbs-ui) - see that repo's `docs/architecture-brief.md`
  for the roadmap

See [`docs/GLOSSARY.md`](docs/GLOSSARY.md) for aviation and domain terms (PIC, dual, unclaimed pilot,
etc.), and `docs/DECISIONS.md` for a dated record of significant architecture/engineering decisions
made along the way.
