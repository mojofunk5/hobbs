# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Working Practices

Carried over from `~/projects/source/things` (same author, same standing rules):

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
- **A database migration must never break the code currently running against it.** Same
  expand/contract discipline as `things` - see that repo's CLAUDE.md for the full reasoning. A new
  `NOT NULL` column only gets a `DEFAULT` if there's a value that's genuinely semantically right for
  a row the old code writes; otherwise expand (nullable) → backfill → contract (`NOT NULL`) across
  separate migrations.

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

## Origin

Scaffolded 2026-08-24 from `~/projects/source/things` (a Javalin-based party-game backend) - the
entire auth/session/admin/referral-code/password-reset subsystem was copied across near-verbatim
with `Player` renamed to `Pilot` and `things`/`Things` renamed to `hobbs`/`Hobbs`, including its test
suite (ported and renamed the same way). See that repo for the original, more battle-tested version
of this code and for the CAP804/FCL.050 logbook research that shaped the domain model below.

## Package Layout

- **`domain/`** - Core business logic. Auth subsystem: `Pilot`, `Pilots`, `Auth`, `Session`,
  `Sessions`, `AdminBootstrap`, `ReferralCode`, `PasswordReset`, `EmailSender`/`SmtpEmailSender` (all
  carried over from `things`). Flight domain (new): `Logbook` (thin orchestration, mirrors
  `Pilots`/`things`' `Games`), `FlightEntry`, `Aircraft`, `FlightTrack`, `SimulatorSession`, plus a
  repository per aggregate
- **`endpoint/`** - Javalin HTTP route handlers: `AuthEndpoint`, `AdminEndpoint`, `PilotEndpoint`
  (auth), `HealthEndpoint` (`/health`, `/version`), `FlightEntryEndpoint`, `AircraftEndpoint` (flight
  domain)
- **`dto/`** - Jackson-serialized request/response objects
- **`mapper/`** - Converts between DTOs and domain objects

## Domain model

See README.md for the full field list. The short version: `FlightEntry` is one CAP804/FCL.050
logbook row (all durations in whole minutes, not float hours); `FlightTrack` is an optional raw GPS
recording that can pre-fill a draft `FlightEntry` but is never required - `flightTrackId` is
nullable, and a manually-entered flight is exactly as valid as a GPS-derived one.

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
- **CI/deploy secrets.** `.github/workflows/build.yml` was carried over and points at `/opt/hobbs` on
  the same VPS as `things`, but the GHCR/SSH secrets for *this* repo haven't been configured yet -
  the workflow won't actually deploy until that's done.
