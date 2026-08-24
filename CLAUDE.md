# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Working Practices

- **Keep documentation in sync.** When making a significant change, update the relevant
  context/documentation files in the same change - this includes `CLAUDE.md`, `README.md`, and
  OpenAPI descriptions. Don't leave stale descriptions behind.
- **Don't auto-merge.** Open a PR and let Andy review it. Only merge when explicitly told to.
- **Before calling a PR ready, confirm it's actually mergeable against the current tip of its base
  branch** - `gh pr view <n> --json mergeable,mergeStateStatus` should say `MERGEABLE`. Re-check
  every other open PR each time one merges.
- **Always run the tests after a code change.** `./gradlew test` before considering a change
  complete.
- **Use the narrowest interface that fits.** `Map`/`List`/`Collection` rather than concrete
  implementations, unless the class genuinely needs the wider API.
- **Never delete a branch, local or remote - no exceptions, not even a merged one, not even
  `master`.** No branch protection available on a private repo without a paid plan, so this is the
  only real backstop against an accidental deletion.
- **A database migration must never break the code currently running against it.** The deploy
  pipeline runs `migrate` immediately before recreating the app container with the same new image
  (see Persistence in README.md), so migration and code normally move together in one deploy - but
  that's not a guarantee: a migration can be run manually ahead of a deploy, or a deploy can fail
  partway through and leave the old container running against the already-migrated schema. Write
  every migration so the *previous* release's code keeps working against the *new* schema - and
  "keeps working" means producing correct rows, not just avoiding a DB error. A new `NOT NULL` column
  only gets a `DEFAULT` if there's a value that's genuinely semantically right for a row the old code
  writes; otherwise expand (nullable) → backfill → contract (`NOT NULL`) across separate migrations.

## Build and Test Commands

```bash
./gradlew build                    # Full build
./gradlew test                     # Run all tests
./gradlew test jacocoTestReport    # Run tests + JaCoCo coverage report
```

Schema migrations are a separate, explicit step from starting the server:
```bash
./gradlew shadowJar
java -cp hobbs-0.0.1-SNAPSHOT-all.jar com.bonney.hobbs.HobbsApplication migrate
java -cp hobbs-0.0.1-SNAPSHOT-all.jar com.bonney.hobbs.HobbsApplication 8080
```

## Package Layout

- **`domain/`** - Core business logic. Auth subsystem: `Pilot`, `Pilots`, `Auth`, `Session`,
  `Sessions`, `AdminBootstrap`, `ReferralCode`, `PasswordReset`, `EmailSender`/`SmtpEmailSender`.
  Flight domain: `Logbook` (thin orchestration over the repositories), `FlightEntry`, `Aircraft`,
  `FlightTrack`, `SimulatorSession`, plus a repository per aggregate
- **`endpoint/`** - Javalin HTTP route handlers: `AuthEndpoint`, `AdminEndpoint`, `PilotEndpoint`
  (auth), `HealthEndpoint` (`/health`, `/version`), `FlightEntryEndpoint`, `AircraftEndpoint` (flight
  domain)
- **`dto/`** - Jackson-serialized request/response objects
- **`mapper/`** - Converts between DTOs and domain objects

Layered, inside-out dependencies: `endpoint` → `mapper` → `dto`, `endpoint` → `domain`. The domain
has no knowledge of HTTP, JSON, or persistence. Identifiers are microtyped (`PilotId`, `AircraftId`,
etc. all extend `TypedId`) rather than passing raw `UUID`s at domain boundaries.

## Domain model

See README.md for the full field list. The short version: `FlightEntry` is one CAP804/FCL.050
logbook row (all durations in whole minutes, not float hours); `FlightTrack` is an optional raw GPS
recording that can pre-fill a draft `FlightEntry` but is never required - `flightTrackId` is
nullable, and a manually-entered flight is exactly as valid as a GPS-derived one.

## Testing notes

Repository tests spin up a fresh in-memory H2 database (PostgreSQL compatibility mode) per test via
Flyway, rather than sharing state. `TIMESTAMP WITH TIME ZONE` columns round to microsecond precision
on write, while `OffsetDateTime.now()` can carry full nanosecond precision on some JVMs - a test that
asserts exact equality on a timestamp after a save/load round trip needs to truncate its expected
value to `ChronoUnit.MICROS` first (see `FlightTrackRepositoryTest`), or it will intermittently fail
whenever `now()`'s sub-microsecond digits aren't already zero.

`RateLimitRepository` takes an injectable `Clock` (defaults to `Clock.systemUTC()`) specifically so
its tests can use `Clock.fixed(...)` instead of racing the real wall clock - its rate-limit window is
"the current second", so a test asserting "the Nth request in the same window is rejected" against
real time would intermittently fail whenever a second ticks over mid-test.

## Open work (not yet built)

- **FlightTrack → FlightEntry derivation.** The actual "GPS did the logbook for you" logic - parsing
  a track into departure/arrival place+time, landing counts (including distinguishing touch-and-goes
  from full-stops), night time (needs sunset/sunrise tables per airfield/date), and cross-country
  distance. Currently the two are just linkable via `flightTrackId`; nothing populates one from the
  other yet.
- **Flutter app.** This repo is the backend only. The mobile/web app - including the background GPS
  recording itself (`flutter_background_geolocation`, proper `Always`/background-service permission
  handling on both platforms, offline-first local buffering since airfields often have poor
  signal) - doesn't exist yet.
- **CI/deploy secrets.** `.github/workflows/build.yml` deploys to a self-hosted VPS via SSH/GHCR, but
  the secrets for *this* repo haven't been configured yet - the workflow won't actually deploy until
  that's done.
