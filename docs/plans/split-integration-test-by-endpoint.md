# Plan: Split `HobbsApplicationIntegrationTest` by endpoint

**Status:** Designed 2026-08-30, not yet implemented.

## Context

While investigating CI speedups (see the now-merged test-parallelism work in `CLAUDE.md`'s git
history), `HobbsApplicationIntegrationTest` turned out to hold 83 of the suite's tests - Andy asked
whether that's too many for one class and how it got that way.

Checked against git history rather than guessed: **72 of the 83 (87%) were there from the very first
commit**, `851a3de` ("Scaffold Hobbs: a self-hosted PPL flight logbook") - this wasn't gradual
accretion, it was a decision made once at scaffolding time to put the entire application's HTTP-level
coverage (auth, referral codes, password reset, admin pilot management, aircraft, flight entries) into
a single class. Only 11 more tests were added across the five feature PRs since, each just extending
the existing file rather than questioning the original scaffold - the path of least resistance once
it was the established "full-stack test" home, with nothing in `CLAUDE.md`'s working practices ever
addressing test-suite organization to prompt reconsidering it.

Confirmed problems with the current shape:
- **Not cohesive** - spans genuinely unrelated subsystems (auth, admin, pilot, aircraft, flight
  entry). A red X on `HobbsApplicationIntegrationTest` says nothing about what broke.
- **Not navigable** - ~950 lines, no internal structure beyond method declaration order.
- **Review blast radius** - a PR chunk touching only one endpoint (e.g. pilot-picker's `GET /pilot`)
  still diffs against a 950-line file mixing every other endpoint's tests around it.
- It was also the file the test-parallelism work had to reason about as a single unit for
  parallel-safety, when only 3 of its 83 tests actually needed special handling.

**Explicit non-goal:** this is not a performance fix. Splitting doesn't reduce total test runtime -
each split-off class pays the same Javalin+Flyway+H2 fixture cost per test the current one does, and
method-level parallelism (already shipped) already runs all of them concurrently regardless of file
boundaries. This plan is about cohesion, navigability, and review blast-radius only.

## Confirmed decisions

- **Split one-to-one with `endpoint/` package boundaries** - `CLAUDE.md`'s own Package Layout section
  already names these six: `HealthEndpoint`, `AuthEndpoint`, `AdminEndpoint`, `PilotEndpoint`,
  `FlightEntryEndpoint`, `AircraftEndpoint`. Six integration test classes, not the three feature-level
  groupings that section also mentions ("auth subsystem" vs "flight domain") - grouping Auth+Admin+
  Pilot back into one class would just recreate a smaller version of the same problem.
- **Naming**: `<Endpoint>EndpointIntegrationTest`, same package (`com.bonney.hobbs.integration`) -
  matches the existing unit-test convention of naming a test class after the class under test
  (`PilotRepositoryTest`, `PilotMapperTest`, etc.), applied to endpoints.
- **Shared fixture logic moves to a package-private `AbstractIntegrationTest` base class** in the same
  package - `@BeforeEach`/`@AfterEach`, the `application`/`httpClient`/`adminClient`/`emailSender`
  fields, and the shared helpers (`createClient()`, `createAuthenticatedClient()`, `register()`,
  `extractResetCode()`), plus the `Fixture` record and `createFixture(Clock)` the three throttle-window
  tests use. Each new `<Endpoint>EndpointIntegrationTest` extends it. Inheritance over composition here
  because the current single class already structures things this way (fields + helpers used
  implicitly by every test) - splitting the fixture into a base class is the minimal-diff move, not an
  opportunity to also redesign the fixture itself.
- **One shared `HobbsClient`** (the Feign client) continues to cover the whole API surface for every
  test class - it's not being split, only the test files and their fixtures are.
- **Cross-cutting tests are placed by primary subject under test**, not mechanically by first
  endpoint touched. A few examples, since these are the ones a mechanical split would get wrong:
  - `anAdminCanInviteAPilotWhoThenRegistersWithThatCode` - asserts admin-invite-then-register works
    end to end; belongs with the other invite-flow tests in `AdminEndpointIntegrationTest`.
  - `anUnclaimedPilotAppearsInTheAdminListWithNullEmailAndDisabled` and
    `deletingAnAccountPreservesTheFlightHistoryUnderTheSamePilotIdAsAnUnclaimedRecord` - both assert
    something about the *admin pilot list's* shape (null email/disabled for an unclaimed record);
    belong in `AdminEndpointIntegrationTest` even though they touch `POST /pilot`/flight entries to
    set up their fixture.
  - The invite-to-claim tests (`invitingAnUnclaimedPilotToClaimLetsThemRegisterAsThatSamePilotId`,
    `onlyTheCreatorOrAnAdminCanInviteAnUnclaimedPilotToClaim`,
    `anAdminCanInviteAnyUnclaimedPilotToClaimEvenWithoutHavingCreatedIt`) exercise
    `POST /pilot/{pilotId}/invite`, a `PilotEndpoint` route despite the name similarity to admin
    invites - belong in `PilotEndpointIntegrationTest`.

## Rough breakdown (for sizing, not a binding checklist)

Counted from the current file; exact placement of a handful of cross-cutting tests is a judgement
call per above, so treat these as approximate:

| New class | Approx. test count | Covers |
| --- | --- | --- |
| `HealthEndpointIntegrationTest` | 3 | `/health`, `/version`, `/openapi` |
| `AircraftEndpointIntegrationTest` | 2 | `POST`/`GET /aircraft` |
| `FlightEntryEndpointIntegrationTest` | 5 | `POST /flight`, `GET /flight`, `GET /flight/{id}` |
| `PilotEndpointIntegrationTest` | ~14 | `POST /pilot`, `PUT`/`DELETE /pilot/{id}`, `GET /pilot?search=`, invite-to-claim |
| `AdminEndpointIntegrationTest` | ~32 | Admin pilot list/invite/disable/delete/expire-sessions/cancel-invite |
| `AuthEndpointIntegrationTest` | ~27 | Register, login, password reset, referral codes, throttling |

## Chunking

Per `CLAUDE.md`'s "keep PRs small" rule - even though this is mechanical (no behavior change), the
source file is large enough that reviewing it all at once defeats the point of splitting it. Smallest/
lowest-risk groups first, to prove the `AbstractIntegrationTest` extraction is right before moving the
bulk of the tests:

1. Extract `AbstractIntegrationTest`; move `HealthEndpointIntegrationTest` and
   `AircraftEndpointIntegrationTest` (5 tests total) as the first, small proof of the pattern.
2. Move `FlightEntryEndpointIntegrationTest` (5 tests).
3. Move `PilotEndpointIntegrationTest` (~14 tests, including the search-pilot tests already landed by
   the pilot-picker work).
4. Move `AuthEndpointIntegrationTest` (~27 tests, including the three throttle-window tests and their
   `Fixture`/`createFixture(Clock)` usage).
5. Move `AdminEndpointIntegrationTest` (~32 tests, the largest group) and delete the now-empty
   `HobbsApplicationIntegrationTest`.

Each chunk: move tests verbatim (no logic changes), confirm `./gradlew test` still passes with the
same total test count as before the chunk, update `CLAUDE.md`/README if they ever start referencing
`HobbsApplicationIntegrationTest` by name (they don't currently, beyond its testing-notes mentions of
"the integration suite" generically).

## Explicitly out of scope (left for later)

- Any reduction in test *runtime* - see the non-goal above.
- Splitting or changing `HobbsClient` itself.
- Revisiting whether true end-to-end (real Javalin + real H2) is the right style for these tests at
  all - out of scope, this plan only reorganizes the existing tests, it doesn't rearchitect them.
