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
| `Build and test` (compile + `:test` + jacoco) | ~52s | ~27-47s | Confirmed, live (varies with how much actually changed) |
| `:test` task alone | ~54s | ~25s | Confirmed, local |
| `Build and test` with Gradle task caching warm | ~52s (original) | ~13s | **Confirmed on real CI** - `Cache hit for restore-key`, `compileJava`/`compileTestJava`/`test`/`jacocoTestReport` all `FROM-CACHE` in the actual log |
| `Build and push image` (Docker) | ~59s | Layer-caching attempt: 60-119s, no real improvement (see below for why) | Layer caching alone didn't fix it; the actual fix (build the jar outside Docker) not yet confirmed on real CI |
| Docs-only commit | full pipeline (~130s+ end to end) | `changes` job only (~5s) | Confirmed working, including surviving a real false-positive bug and fix (see below) |

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

### Genuine Docker layer caching for dependency resolution - real, but not the actual bottleneck
See "What didn't work" below for the failed first attempt. The version that worked: copy only
`build.gradle`/`settings.gradle`/the Gradle wrapper first, run `./gradlew dependencies` to resolve
(download) everything into that layer, *then* copy the rest of the source and run the real build.
Standard package-manager Docker pattern - the same reason a Node image does `COPY package.json`
before `COPY . .`. Confirmed on a real deploy: the build log shows `CACHED` for that layer.

**This did not fix deploy time staying flat, and Andy correctly called that out** - real numbers
across three deploys after this landed: 60s, 79s, 119s, against a 59s original baseline. Diagnosed by
timing each numbered step in the actual `docker/build-push-action` log rather than trusting the
`CACHED` marker as proof of a win: the dependency layer's own import cost 8.1s (not free - the `gha`
cache backend still downloads the real layer bytes over the network), but the *real* cost was
`RUN ./gradlew shadowJar -x test --no-daemon` itself - **30.8s**, completely unaffected by this fix.
The assumption going in was that dependency download was the dominant cost; it wasn't, and splitting
it out just made the actual bottleneck (compile + `generateJooq` + fat-jar packaging, all re-running
from scratch in a fresh, cache-less container on every single deploy) visible as its own number
instead of fixing it. See "Building the jar outside Docker entirely" below for the actual fix.

### Building the jar outside Docker entirely
The real fix for the above: build the jar in the `build` job (which already has a warm Gradle build
cache - see the build-cache entry above) and upload it as an artifact; the `deploy` job's Dockerfile
now just `COPY`s the pre-built jar into the runtime image, no JDK stage, no Gradle invocation, no
compilation inside Docker at all. Verified locally before shipping that `shadowJar` itself is
genuinely cacheable, not just `compileJava` - `./gradlew --build-cache shadowJar -x test` with `build/`
wiped and no source changes: 17s (cache cold) -> 6s (`shadowJar FROM-CACHE`). Also smoke-tested the
built jar directly (`java -cp ... HobbsApplication migrate` against a throwaway H2 database) to
confirm the artifact itself is unaffected by *how* it was built - a real Flyway migration ran
successfully, same as it always has.

**Correction, same day**: the first real deploy after this merged failed outright -
`COPY build/libs/hobbs-0.0.1-SNAPSHOT-all.jar app.jar: "not found"`. `.dockerignore` excludes `build`
wholesale (a leftover from when the old Dockerfile did its own `COPY . .` and needed the compiled
output kept out of the image) - BuildKit silently skips a file under an ignored directory rather than
erroring at the ignore-check itself, so this wasn't caught by anything short of a real run. Everything
that *could* be verified locally (that `shadowJar` is cacheable, that the built jar itself runs
correctly) was - this specific failure mode only exists in the download-artifact-into-a-dockerignored-
path interaction, which has no local equivalent to test against. Fixed by downloading the jar to the
repo root instead of `build/libs/` - simpler than fighting `.dockerignore`'s gitignore-style
negation-pattern semantics to carve out an exception.

### Skipping CI for docs-only commits, safely
Split into an always-running `changes` job feeding a job-level `if:` on `build`. See "What didn't
work" for why this has to be a job-level skip (not workflow-trigger-level), and separately for a real
bug in how "is this commit docs-only" was originally detected. The `changes` job now does that
detection itself with a plain shell loop (`git diff --name-only` against the previous commit, checked
file-by-file) rather than a third-party action, specifically because the action's option that looked
right turned out not to mean what it looked like it meant.

### Gradle task output caching + configuration caching
`gradle.properties` (`org.gradle.caching=true`, `org.gradle.configuration-cache=true`). Verified
locally before shipping, correcting an earlier guess in this doc's own "Open opportunities" section
about `generateJooq` specifically (see below) - `compileJava`/`compileTestJava`/`test`/
`jacocoTestReport` are all genuinely cacheable, and Gradle's ABI-aware "compile avoidance" means
`compileTestJava`/`test` can still hit `FROM-CACHE` even when a main source file changes, as long as
the change doesn't affect any public API surface (a comment, a private method body, a log statement -
the common case for most ordinary commits, not just identical-source reruns). Measured locally with
`test jacocoTestReport`: ~35s (build cache present but nothing cached yet) -> ~8s (no source changes)
-> ~12s (one main source file changed, comment-only, so compile avoidance still kicks in for
downstream tasks). Configuration cache adds a further, smaller win on top (~8s -> ~6s locally).
Confirmed compatible with every task graph CI actually runs (`test jacocoTestReport`,
`dependencyUpdates`, `shadowJar` for the Docker build) - none errored or warned about configuration
cache incompatibility.

Getting this to actually persist across CI runs needed a second fix - see "What didn't work" for why
the first version's assumption about `setup-java`'s cache was wrong.

## What didn't work (and why)

### Assuming `setup-java`'s `cache: gradle` covers the Gradle build cache
It doesn't - confirmed by checking two consecutive real CI runs' logs for `FROM-CACHE` and finding
none in either, after `org.gradle.caching=true` had genuinely been enabled and merged. `setup-java`'s
Gradle caching is scoped to dependency artifacts and the wrapper; the build cache lives in a sibling
directory (`~/.gradle/caches/build-cache-1`) that option never touches, despite the "same parent
directory" reasoning sounding plausible enough to ship without checking first. Fixed with an explicit
`actions/cache` step targeting that path directly, using a per-run key plus a prefix `restore-keys`
(the standard pattern for a cache that accumulates over time rather than being invalidated wholesale -
the build cache is already content-addressed internally by task input hashes, so restoring a slightly
stale snapshot just means some entries go unused, not incorrect results).

**Confirmed fixed on the next real run**: `Cache hit for restore-key: gradle-build-cache-Linux-...`,
followed by `compileJava`/`compileTestJava`/`test`/`jacocoTestReport` all showing `FROM-CACHE` in the
actual log. `Build and test` dropped to ~13s on that run, down from the ~52s original baseline - the
clearest single confirmed win in this whole document.

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

### `dorny/paths-filter`'s `predicate-quantifier: every` doesn't mean "every changed file matches"
It means "every declared *pattern* has at least one matching file" - a materially different relation.
With filters `['**/*.md', 'docs/**']`, a single changed doc file satisfies *both* patterns on its own,
so `every` was satisfied regardless of what else changed - not "every changed file is a doc" as
intended. Real impact, not just theoretical: a PR ([hobbs#23](https://github.com/mojofunk5/hobbs/pull/23))
that changed `gradle.properties` (real config) alongside `docs/CI_PERFORMANCE.md` got misclassified
docs-only and had its entire `build`/`deploy` silently skipped on merge - the actual Gradle-caching
change that PR shipped was never run through CI at all. Checked the blast radius across every run
since the docs-only-skip mechanism was introduced (`gh run list` + checking each `build` job's
conclusion against that commit's actual file list): only this one merge was affected, everything else
correctly ran or correctly skipped. Fixed by replacing the action with a plain shell loop over
`git diff --name-only`, checking each changed file individually - verified locally against five cases
(docs-only, the exact mixed-file bug scenario, code-only, a nested `docs/` path, and a filename that
merely contains "docs" without being under the `docs/` directory) before shipping the fix.

### The fix above's own `BASE` was wrong for PR branches that merge master back in
The hand-rolled replacement compared each commit against `github.event.before` - correct for `master`
(sequential merges only) but wrong for a feature branch once it merges `master` back into itself, a
normal thing to do to stay current. That merge commit's diff against `github.event.before` (the
branch's own previous tip) necessarily includes everything `master` changed in the meantime, not just
the branch's own content. Confirmed on `hobbs-ui`: a genuinely docs-only PR
([hobbs-ui#21](https://github.com/mojofunk5/hobbs-ui/pull/21)) merged `master` in, incidentally
picking up an unrelated CI workflow change, and got wrongly classified as *not* docs-only as a result
- a full unnecessary build ran for a PR that only ever touched documentation. The opposite direction
of mistake from the `paths-filter` bug above (that one wrongly skipped a real build; this one wrongly
ran an unnecessary one), but the identical root cause: comparing against the wrong base commit.
Diagnosed by walking the merge commit's own parents (`git log --graph`, `git show --no-patch --format
"%H %P"`) to find exactly which commit's diff was actually being computed, rather than guessing.
Fixed by branching on `github.ref`: `github.event.before` only for pushes to `master`, `git merge-base
origin/master HEAD` for every other branch - verified locally against the exact bug commit before
shipping (diffing against the correct historical merge-base reproduced the expected two-file,
docs-only result).

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

### Guessing `generateJooq` was cacheable, instead of checking
An earlier version of this doc's "Open opportunities" section reasoned that `generateJooq` was
"plausibly cacheable" since it only depends on the migration SQL files, not the whole source tree -
sounded right, was wrong. Checked with `./gradlew --build-cache generateJooq --info` before writing
the fix below: the `nu.studer.jooq` plugin's task hardcodes `Task.upToDateWhen is false`, meaning it
always considers itself out of date and re-executes regardless of the build cache, migrations included
- Gradle even computes and stores a cache key for it, but never has a chance to load from one. This
repo's own `docs/CI_PERFORMANCE.md`/`DECISIONS.md` convention exists partly to keep a record like this
honest - worth leaving the wrong guess visible rather than quietly fixing it, per that convention.

## Open opportunities

### Confirm building the jar outside Docker actually fixes deploy time
Verified locally (`shadowJar FROM-CACHE`, and the built jar smoke-tested directly), but not yet run on
real CI. Watch the next real deploy's `Build and push image` step - should drop to roughly base-image-
pull-plus-push time (a few seconds) rather than the 60-119s the layer-caching-only version still took,
since there's no compilation left inside the Docker build at all. If a deploy follows a commit that
changed main source significantly, expect the `build` job's own `Build and test` step to absorb that
cost instead (still cache-assisted via compile avoidance where possible) - the total pipeline time
matters more than any single job's number now that the work moved, not disappeared.

### Untouched: `dependencyUpdates` step, jacoco report generation
Both cheap already (~4-6s and ~1.5s respectively per the `--profile` breakdown) - not pursued given
the size, but not measured for further headroom either.
