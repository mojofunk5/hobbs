# Hobbs

An electronic PPL flight logbook. Log flights the old-fashioned way (a form matching the UK CAA/EASA
standard logbook - CAP804 / FCL.050) or hit record before you fly and let GPS pre-fill the entry for
you to confirm afterwards. GPS recording is always optional - a manually-entered flight is just as
valid as one derived from a recorded track, and a track that cuts out partway through still pre-fills
whatever it captured.

Not a replacement for your paper logbook - the UK CAA still requires that as the primary record. This
is a second, self-hosted copy that's much harder to lose.

## Running locally

Schema migrations are a separate, explicit step from starting the server (see `things`' own README
for the reasoning - this repo follows the same pattern). Run `migrate` once before the first run
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

Brings up Postgres and the app only, at `http://localhost:8080`. A `hobbs-ui` repo (Flutter web
build) and its own Caddy compose stack are the planned frontend - not built yet.

## Running tests

```bash
./gradlew test
./gradlew test jacocoTestReport
```

248 tests, 94% instruction / 84% branch coverage: the auth/session/referral-code/admin/password-reset
subsystem carried over from `things` (both unit tests and most of `things`' integration-test
scenarios ported and renamed the same way as the production code), plus new tests for the flight
domain - `LogbookTest` (mocked repositories), `AircraftRepositoryTest`, `FlightEntryRepositoryTest`,
`FlightTrackRepositoryTest`, `SimulatorSessionRepositoryTest` (real H2 via Flyway, same pattern as
`PilotRepositoryTest`), value-object tests (`PilotTest`, `AircraftTest`, `FlightEntryTest`,
`FlightTrackTest`, `SimulatorSessionTest`), `AppConfigTest`, and `HobbsApplicationIntegrationTest`
(full Javalin+H2 stack driven through a new `HobbsClient`, covering register/login, admin/referral-
code flows, and aircraft/flight-entry CRUD including the 401/403/404 auth-boundary cases).

`RateLimitRepositoryTest` uses an injectable `Clock` (added to `RateLimitRepository` itself, same fix
applied to `things`) rather than the real wall clock - the fixed-window rate limiter's window is "the
current second", so asserting against real time was intrinsically flaky whenever a second ticked over
mid-test.

## Architecture

Same conventions as `~/projects/source/things` (a Javalin party-game backend) - see that repo's
README/CLAUDE.md for the full rationale. In short:

- **Layered, inside-out dependencies**: `endpoint` → `mapper` → `dto`, `endpoint` → `domain`. The
  domain has no knowledge of HTTP, JSON, or persistence.
- **Thin service objects, rich domain model**: `Logbook` and `Pilots` are orchestration shells wiring
  repositories to domain objects; behaviour lives in the domain classes themselves.
- **Microtypes for identifiers**: `PilotId`, `AircraftId`, `FlightEntryId`, `FlightTrackId`,
  `SimulatorSessionId` all extend `TypedId` rather than passing raw `UUID`s at domain boundaries.
- **REST over HTTP with OpenAPI documentation inline**, Javalin routes with `@OpenApi` annotations
  colocated with handlers.

### Auth

Carried over from `things` near-verbatim, `Player` renamed to `Pilot` throughout (the account holder
*is* the pilot who owns the logbook here, so the concept maps directly): session-based auth
(`Bearer <sessionId>`), admin bootstrap on first run, and registration gated behind an admin-issued,
single-use, email-scoped referral code. Public repo, public app, but registration is deliberately
hard to come by - see the parent project's notes on why.

## Domain model

Based on the UK CAA/EASA standard logbook format (CAP804 = FCL.050 template):

- **Pilot** - the account holder / logbook owner (`pilot` table, same shape as `things`' `player`)
- **Aircraft** - registration, make, model, engine category. Shared across pilots, not scoped to one
  account (a club trainer only needs registering once)
- **FlightEntry** - one row of the logbook: date, departure/arrival (place + time), PIC name, and
  every duration (single/multi-engine, total, night, IFR, cross-country, PIC, co-pilot, dual,
  instructor) plus day/night landings and remarks. All durations are stored in whole minutes, not
  float hours, to avoid rounding drift across hundreds of entries. `flightTrackId` is nullable and
  optional - see below.
- **FlightTrack** - a raw GPS recording (points stored as a single JSON blob for now, not one row per
  point - see the class Javadoc for why). Feeds a *draft* `FlightEntry` that the pilot confirms or
  corrects; never writes a `FlightEntry` on its own.
- **SimulatorSession** - FSTD (simulator) time, its own row shape in the real logbook, kept separate
  from `FlightEntry`.

## Not yet built

- Deriving a draft `FlightEntry` from a `FlightTrack` (departure/arrival detection, landing counting,
  night-time-from-sunset-tables, cross-country distance) - currently `FlightEntry` and `FlightTrack`
  exist as separate persisted things with no automatic bridge between them yet
- The Flutter mobile app (iOS/Android/web) - the actual GPS recording UI doesn't exist yet; this repo
  is the backend only
- Pagination/filtering on `GET /flight` (currently returns everything for the authenticated pilot)
