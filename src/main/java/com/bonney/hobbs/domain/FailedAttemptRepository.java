package com.bonney.hobbs.domain;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.bonney.hobbs.jooq.Tables.FAILED_ATTEMPT;

// Fixed-window counter keyed by (attempt_key, purpose) rather than client IP - same shape as
// RateLimitRepository (a single atomic INSERT ... ON CONFLICT DO UPDATE upsert, window identity
// computed in application code so the SQL only ever needs an equality check, never date
// arithmetic), generalized to an arbitrary window duration instead of a hardcoded one second.
// Same accepted boundary-imprecision trade-off as RateLimitRepository: a caller could in theory
// get roughly double the configured limit right at a window boundary. At the limits this is
// actually used with (10 login attempts/15 min, 5 reset-code attempts/15 min) even a doubled
// worst case is nowhere near enough to brute-force a real password or a 6-digit reset code before
// its own 30-minute TTL expires.
public class FailedAttemptRepository {

    private final DSLContext dsl;
    private final Clock clock;

    public FailedAttemptRepository(DSLContext dsl) {
        this(dsl, Clock.systemUTC());
    }

    // Clock is injectable so tests can fix "now" instead of racing the real wall clock - same
    // reasoning as RateLimitRepository's own Clock: without this, a test asserting "the Nth+1
    // attempt in the same window is still throttled" is intrinsically flaky, failing whenever the
    // window rolls over between recording the Nth failure and checking the (N+1)th - rare with a
    // 15-minute window (unlike RateLimitRepository's one-second window) but not impossible, since
    // the window boundary is epoch-aligned rather than relative to when the test itself started.
    public FailedAttemptRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    // Read-only, called before doing any password/code verification so an already-throttled caller
    // doesn't pay for a wasted bcrypt check.
    public boolean isThrottled(String key, FailedAttemptPurpose purpose, int limit, Duration window) {
        Integer count = dsl.select(FAILED_ATTEMPT.ATTEMPT_COUNT)
                .from(FAILED_ATTEMPT)
                .where(FAILED_ATTEMPT.ATTEMPT_KEY.eq(key))
                .and(FAILED_ATTEMPT.PURPOSE.eq(purpose.name()))
                .and(FAILED_ATTEMPT.WINDOW_START.eq(currentWindowStart(window)))
                .fetchOne(FAILED_ATTEMPT.ATTEMPT_COUNT);
        return count != null && count >= limit;
    }

    // Called only after a genuine failure (wrong password, unknown identifier, wrong/expired/used
    // reset code) - a successful attempt does not reset the counter, deliberately as simple as
    // RateLimitRepository (the window just ages out on its own).
    public void recordFailure(String key, FailedAttemptPurpose purpose, Duration window) {
        OffsetDateTime windowStart = currentWindowStart(window);

        dsl.insertInto(FAILED_ATTEMPT)
                .set(FAILED_ATTEMPT.ATTEMPT_KEY, key)
                .set(FAILED_ATTEMPT.PURPOSE, purpose.name())
                .set(FAILED_ATTEMPT.WINDOW_START, windowStart)
                .set(FAILED_ATTEMPT.ATTEMPT_COUNT, 1)
                .onConflict(FAILED_ATTEMPT.ATTEMPT_KEY, FAILED_ATTEMPT.PURPOSE)
                .doUpdate()
                .set(FAILED_ATTEMPT.ATTEMPT_COUNT,
                        DSL.when(FAILED_ATTEMPT.WINDOW_START.eq(windowStart), FAILED_ATTEMPT.ATTEMPT_COUNT.plus(1))
                                .otherwise(1))
                .set(FAILED_ATTEMPT.WINDOW_START, windowStart)
                .execute();
    }

    // Run periodically by ScheduledCleanupJobs. An hour is comfortably longer than any window this
    // is actually configured with (currently up to 15 minutes) and the 30-minute sweep interval, so
    // nothing live ever gets swept.
    public void deleteStale() {
        dsl.deleteFrom(FAILED_ATTEMPT)
                .where(FAILED_ATTEMPT.WINDOW_START.lt(OffsetDateTime.now(clock).minusHours(1)))
                .execute();
    }

    // Deterministic from "now" and the window length alone, so isThrottled and recordFailure agree
    // on the current window's identity without either needing to read the other's prior state first
    // - the same trick RateLimitRepository uses via truncatedTo(SECONDS), generalized to an
    // arbitrary duration via epoch-second bucketing.
    private OffsetDateTime currentWindowStart(Duration window) {
        long windowSeconds = window.getSeconds();
        long nowEpoch = OffsetDateTime.now(clock).toEpochSecond();
        long bucketEpoch = (nowEpoch / windowSeconds) * windowSeconds;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(bucketEpoch), ZoneOffset.UTC);
    }
}
