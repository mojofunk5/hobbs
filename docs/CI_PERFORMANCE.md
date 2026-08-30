# CI performance

A record of what's been tried to speed up this repo's pipeline (`.github/workflows/build.yml`), what
actually worked, what didn't (and why), and what's still open. Complements
[`docs/DECISIONS.md`](DECISIONS.md)'s terser entry - this doc has the full numbers and reasoning.
Never delete a section here, same convention as `DECISIONS.md` - if something gets superseded, say so
rather than removing the record of what was tried.

All timings are wall-clock from real GitHub Actions runs (via `gh run view`) unless marked "local" -
CI numbers vary run to run, so treat these as representative, not exact.

## Summary

| Stage | Before | After | Status |
| --- | --- | --- | --- |
| `Build and test` (compile + `:test` + jacoco) | ~52s | ~38s | Confirmed, live |
| `:test` task alone | ~54s | ~25s | Confirmed, local |
| `Build and push image` (Docker) | ~59s | ~71-81s (attempt 1, reverted) | **Regressed**, then fixed - see below |
| Docs-only commit | full pipeline (~130s+ end to end) | `changes` job only (~5s) | Confirmed working, not yet timed on a real docs-only `hobbs` commit |

## What worked

### Test parallelism
JUnit 5 was already capable of running independent tests concurrently; nothing was doing so. Every
test class in the suite builds its own isolated fixture per test method (a fresh in-memory H2
database with a random name; `HobbsApplicationIntegrationTest` additionally boots its own Javalin
server on an ephemeral port) via `@BeforeEach`, so methods are exactly as independent as separate
classes are - see `CLAUDE.md`'s testing notes, written before this work but describing exactly the
isolation that makes concurrent execution safe.

`src/test/resources/junit-platform.properties` turns this on, with `SmtpEmailSenderTest` opted out
via `@Execution(ExecutionMode.SAME_THREAD)` since it's the one class sharing state (a GreenMail
server) across its own methods via `@BeforeAll` rather than per-method `@BeforeEach`.

### Genuine Docker layer caching
See "What didn't work" below for the failed first attempt. The version that actually works: copy only
`build.gradle`/`settings.gradle`/the Gradle wrapper first, run `./gradlew dependencies` to resolve
(download) everything into that layer, *then* copy the rest of the source and run the real build.
Standard package-manager Docker pattern - the same reason a Node image does `COPY package.json`
before `COPY . .`. The dependency-resolution layer only invalidates when the manifest files
themselves change, not on every commit.

### Skipping CI for docs-only commits, safely
Split into an always-running `changes` job (using `dorny/paths-filter` with
`predicate-quantifier: every`, so it only reports docs-only when *every* changed file matches - a
commit touching `CLAUDE.md` alongside real code must still run the full build) feeding a job-level
`if:` on `build`. See "What didn't work" for why this has to be a job-level skip, not a
workflow-trigger-level one.

## What didn't work (and why)

### `--mount=type=cache` for the Docker build's Gradle cache
Looked right, made things worse: three consecutive real deploys came in at 96s and 71s against a 59s
baseline, not faster. A BuildKit cache *mount* is local state tied to the runner's own disk - it's
never included in what `cache-to: type=gha` exports (that backend only exports image layers), so on
GitHub Actions' ephemeral runners (a fresh VM every job) it provides zero benefit. Confirmed in the
build logs: Gradle re-downloaded its own distribution zip from scratch on a run that should have been
a cache hit. The ~15s `docker/setup-buildx-action` overhead this approach also needed was pure loss on
top. Diagnosed by reading the actual `gh run view --log` output rather than assuming the fix worked
because it looked correct.

### Trigger-level `paths-ignore` to skip docs-only commits
Simple, and actively wrong on a repo with a required status check. Branch protection is a GitHub
feature offered free only on *public* repos, not private ones - `hobbs` is public (see
`docs/DECISIONS.md`'s 2026-08-29 entry) and has had it enabled since, requiring the `build` check
(`gh api repos/mojofunk5/hobbs/branches/master/protection` confirms it). `CLAUDE.md`'s Working
Practices had gone stale in the meantime and still claimed "no branch protection available on a
private repo" as this repo's own situation, rather than being updated once `hobbs` actually went
public and picked it up - caught only by checking the live API directly rather than trusting the
docs from memory. If the whole workflow never triggers for a commit, `build` never posts any status
for it at all - GitHub shows that required check stuck "waiting to be reported" indefinitely and
blocks merging, the opposite of the goal. Fixed with the job-conditional pattern described above
instead.

### Over-conservative test parallelism (first pass)
The first version of `junit-platform.properties` defaulted test *methods* to `same_thread`, only
parallelizing across classes - guarding against a real risk (`SmtpEmailSenderTest`'s shared fixture)
but at the cost of leaving `HobbsApplicationIntegrationTest` (83 of the suite's tests, almost
certainly the dominant share of runtime) fully serial regardless of core count. Grepping the suite
(`grep -rln "private static [A-Z]" src/test`) confirmed that class is the *only* one with any shared
state across its own methods - the blanket caution wasn't needed everywhere it was applied. Flipping
the default to concurrent and opting out only that one class got the real win, but also surfaced a
genuine (rare) race in `FailedAttemptRepository` that the conservative version had been masking rather
than avoiding - see the Clock-injection fix in `DECISIONS.md`.

## Open opportunities

### Gradle build/configuration caching
Not yet investigated. `setup-java`'s `cache: gradle` already caches downloaded *dependency* artifacts
across CI runs, but not Gradle's own *task output* cache - `generateJooq`, `compileJava`, and
`compileTestJava` (~10s combined of the current `Build and test` step, per `--profile`) re-run from
scratch on every single CI run regardless of whether their actual inputs changed. Two separate,
composable levers worth testing empirically before trusting either (per this doc's own lesson about
verifying rather than assuming):

- **Gradle build cache** (`org.gradle.caching=true` + a persisted cache directory across runs, e.g.
  via `actions/cache` keyed on a hash of the relevant inputs) - lets a task skip re-execution entirely
  when its inputs are unchanged from a previous run. `generateJooq` specifically only depends on the
  migration SQL files, not the whole source tree, so it's plausibly cacheable across most ordinary
  feature-work commits (which don't touch migrations) even though `compileJava`/`compileTestJava`
  would still miss on most commits (source changes almost every commit).
- **Gradle configuration cache** (`org.gradle.configuration-cache=true`) - separate from the build
  cache, speeds up the *configuration* phase (evaluating `build.gradle` itself) rather than task
  execution. Gradle's own CLI output already nags about this on every run
  ("Consider enabling configuration cache...").

Given the small absolute size here (~10s), the honest expectation is a modest win, not a dramatic one
- but worth doing properly (measured, not assumed) given it's a real interest, not just theoretical.

### Confirm the fixed Docker layer caching on a second real deploy
The corrected Dockerfile (see "What worked" above) has only had its first run, which necessarily pays
the cost of populating the new dependency layer for the first time (81s, no better than baseline yet)
- same shape as every other cache fix in this doc needing a second run to prove itself. Needs
confirming on the next real deploy that follows an ordinary code change.

### Untouched: `dependencyUpdates` step, jacoco report generation
Both cheap already (~4-6s and ~1.5s respectively per the `--profile` breakdown) - not pursued given
the size, but not measured for further headroom either.
