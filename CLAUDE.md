# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Working Practices

- **Record significant decisions in `docs/DECISIONS.md`, never delete an entry.** Same convention
  `things/docs/BACKLOG.md` states for itself: a decision that's later superseded gets a note pointing
  to what replaced it, rather than being removed - the file is a durable record, not a live TODO
  list. Add an entry whenever a non-obvious architecture or engineering choice gets made (a tradeoff
  was weighed, an alternative was considered and rejected, something departs from an established
  pattern) - not for routine feature work that doesn't involve a real decision.
- **The reverse proxy/TLS for this backend lives in the separate shared [`caddy`]
  (https://github.com/mojofunk5/caddy) repo, not here or in `hobbs-ui`.** Don't assume this repo owns
  its own Caddy config - it doesn't, and hasn't since `hobbs-ui` needed the same host ports 80/443
  that a per-repo Caddy container can't share.
- **Keep documentation in sync.** When making a significant change, update the relevant
  context/documentation files in the same change - this includes `CLAUDE.md`, `README.md`, and
  OpenAPI descriptions. Don't leave stale descriptions behind.
- **Design in a doc PR, merged and reviewed, before any implementation code is written.** For
  non-trivial work, the plan (a `docs/plans/*.md` doc, `docs/DECISIONS.md`/`docs/GLOSSARY.md`
  updates, backlog entries in this file/`README.md`) is its own PR, reviewed and merged on its own -
  see `docs/plans/pilot-account-split.md`/`docs/plans/logbook-entries.md`/`docs/plans/pilot-picker.md`
  for the shape. Implementation then starts as a **new session** against the merged doc, not a
  continuation of the planning conversation. The doc is written to carry all the context forward on
  its own (decisions made and why, trade-offs weighed, what's explicitly out of scope) so the
  implementation session only needs to read it, not have the planning conversation replayed into it.
  This keeps planning free to explore and change direction without burning implementation budget on
  the exploration, and keeps each implementation session small and focused on exactly what the doc
  says to build - the same motivation as "keep PRs small" below, one level up.
- **Don't auto-merge.** Open a PR and let Andy review it. Only merge when explicitly told to.
- **Keep PRs small.** When implementing a multi-part plan (a new migration, several new domain
  classes/endpoints, a full test-suite update), look for natural seams and split the work into a
  sequence of smaller PRs rather than landing it all in one - e.g. schema/migration + core domain
  classes as PR 1, new endpoints as PR 2, admin-side rewiring as PR 3. If a design flaw turns up
  mid-review (e.g. a follow-up correction), prefer a small follow-up PR over pushing another commit
  onto an already-large one already under review, unless the fix is trivial or review hasn't started.
- **Before calling a PR ready, confirm it's actually mergeable against the current tip of its base
  branch** - `gh pr view <n> --json mergeable,mergeStateStatus` should say `MERGEABLE`. Re-check
  every other open PR each time one merges.
- **Always run the tests after a code change.** `./gradlew test` before considering a change
  complete.
- **Keep dependencies current as a matter of course.** `./gradlew dependencyUpdates` (see Dependency
  Freshness Check below) reports anything behind its latest stable version; CI runs it on every push,
  PRs included, and uploads the result as an artifact each time - so it's visible during review, not
  just after landing on `master`. When that report shows something outdated - whether noticed from
  the CI artifact or from running the check locally - bump it rather than leaving it to accumulate; a
  small, mechanical version bump is cheap now and gets riskier the longer it's deferred. Batch
  several trivial bumps into one PR; anything with a real migration risk (a major version, a noted
  breaking change) can be its own PR instead. If a specific dependency genuinely shouldn't be bumped
  yet, don't just skip it silently - record why with a version constraint (`reject(...)` + `because`)
  per `build.gradle`'s documented example, so the reason is visible to the next person and the report
  stops re-flagging it.
- **Use the narrowest interface that fits.** `Map`/`List`/`Collection` rather than concrete
  implementations, unless the class genuinely needs the wider API.
- **Never delete a branch, local or remote - no exceptions, not even a merged one, not even
  `master`.** This repo is *public* on GitHub, which is what makes branch protection (see below)
  available at all - GitHub only offers it for free on public repos, not private ones, so a private
  repo would have nothing backstopping this rule but the rule itself. `hobbs` being public means
  branch protection now blocks deleting `master` directly too (see `docs/DECISIONS.md`'s 2026-08-29
  entry - enabled since, not from day one), but not any other branch, so this rule is still the only
  real backstop everywhere else.
- **Branch protection requires the `build` status check before merging** (`gh api
  repos/mojofunk5/hobbs/branches/master/protection` confirms it, same as `hobbs-ui` - both repos are
  public) - a workflow change that stops `build` from ever reporting a status for some commits (e.g.
  a trigger-level `paths-ignore`) will leave affected PRs stuck un-mergeable, not just skip CI
  harmlessly. See `docs/CI_PERFORMANCE.md` for a worked example.
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

### Dependency Freshness Check

```bash
./gradlew dependencyUpdates   # report everything (and the Gradle wrapper) behind its latest stable version
```

Backed by [gradle-versions-plugin](https://github.com/ben-manes/gradle-versions-plugin) (`build.gradle`'s `dependencyUpdates` config) - a community plugin, not an official Gradle one, but the de facto standard for this. Pre-release candidates (alphas/betas/RCs/milestones) are filtered out of the report unless the pinned version is already a pre-release itself - see the `isNonStable` snippet's comment for the Guava `-jre` carve-out. Informational only, not wired into `check`/`build` - a new upstream release can't fail CI on its own. `outputFormatter = 'plain,json'` writes both formats to `build/dependencyUpdates/`. CI runs it on every push (PRs included, so it's visible during review), uploads `report.txt` as a build artifact, and separately parses `report.json` with `jq` into a markdown table appended to the run's job summary (see Working Practices above for the standing rule on acting on it) - so nobody has to download an artifact just to see what's outdated. A dependency deliberately not being bumped yet gets a documented `constraints { ... reject(...) because '...' }` block (example in `build.gradle`) rather than being silently left to keep nagging the report.

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

See [`docs/GLOSSARY.md`](docs/GLOSSARY.md) for aviation and domain terms (PIC, dual, unclaimed
pilot, etc.) and README.md for the full field list. The short version: `FlightEntry` is one CAP804/FCL.050
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
- **`SimulatorSession`/`FlightTrack` referencing a co-pilot's `PilotId`.** `Pilot` and `Account` are
  now split (see [`docs/plans/pilot-account-split.md`](docs/plans/pilot-account-split.md),
  implemented), and the logbook-entries plan
  ([`docs/plans/logbook-entries.md`](docs/plans/logbook-entries.md)) gave `FlightEntry` real
  `pilotInCommandId`/`coPilotId` fields referencing `Pilot` - but `SimulatorSession` and
  `FlightTrack` don't reference a co-pilot's `PilotId` yet. A separate, later plan's decision
  entirely.
- **Merging two `Pilot` records.** If someone registers their own account (a fresh `PilotId`) instead
  of using an invite that would've attached them to an unclaimed record someone else created, there's
  no way to reconcile the two afterwards. Deliberately out of scope for the pilot/account split.
- **Pasted-in ids when adding a flight entry.** The logbook-entries plan
  ([`docs/plans/logbook-entries.md`](docs/plans/logbook-entries.md)) has `PilotId`/`AircraftId`/place
  entered as raw text - workable for one real user's worth of test data, not for real use. Split into
  three separate stories, since each has a different scoping/data-source question:
  - **Picking a pilot you've flown with.** Backend done - see
    [`docs/plans/pilot-picker.md`](docs/plans/pilot-picker.md): `GET /pilot?search=` returns pilots
    privacy-scoped by relationship (you only see someone if you created them or you've logged a
    flight with them, plus yourself) - not a global list. `hobbs-ui`'s picker widget (chunk 2 of the
    plan) not yet built. Aircraft doesn't need this scoping: it's already modelled as shared across
    all pilots (see `AircraftEndpoint`'s doc comment).
  - **Picking an aircraft.** Backend done - see
    [`docs/plans/aircraft-picker.md`](docs/plans/aircraft-picker.md): `Aircraft` is now reference
    data seeded/reconciled from OpenSky's aircraftDatabase.csv (the `import-aircraft` CLI
    subcommand), not pilot-submitted - `POST /aircraft` is gone. `GET /aircraft?search=` (required,
    minimum 2 characters, capped at 50 results) replaces the old unfiltered `GET /aircraft`.
    `hobbs-ui`'s picker widget and Browse Aircraft page (chunk 4 of the plan) not yet built. Unlike
    the pilot picker, there's no "add aircraft" affordance at all - gap-filling for aircraft missing
    from the seed is a deliberately separate, not-yet-built later story (an admin-only
    AeroDataBox-backed single-registration lookup - see the plan doc's Out of scope section).
  - **Picking or registering a location (departure/arrival place).** Not yet designed, and bigger
    than the other two: `FlightEntry.departurePlace`/`arrivalPlace` are still plain `String`s today,
    not an id referencing a real entity - see [`FlightEntry.java`]
    (src/main/java/com/bonney/hobbs/domain/FlightEntry.java). Needs a domain decision before any UX
    work: airfields are a finite, known set, so a self-owned `Location`/`Airfield` reference table
    (seeded from a public ICAO dataset, carrying coordinates) is likely the right call over a live
    Google Maps dependency - especially since night-time-from-sunset-tables (see FlightTrack →
    FlightEntry derivation, above) already wants per-airfield coordinates, not a free-text name.
    Andy to confirm before this becomes a plan doc.
- **Editing/deleting a flight entry.** The logbook-entries plan only covers create/view/list -
  there's no way to fix a mistyped entry or remove one yet.
- **Logbook screens, photo-to-logbook OCR, GPS-recording-to-logbook, and the iOS app** all live in
  [`hobbs-ui`](https://github.com/mojofunk5/hobbs-ui), not here - see that repo's
  `docs/architecture-brief.md` for the roadmap.

The Flutter app (`hobbs-ui`) is built and deployed - live at
[hobbs.bssd.co.uk](https://hobbs.bssd.co.uk). CI/deploy secrets for this repo are configured and
working; `.github/workflows/build.yml` deploys to the VPS on every push to `master`.
