package com.bonney.hobbs.domain;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static com.bonney.hobbs.jooq.Tables.RATE_LIMIT_BUCKET;

// Fixed-window counter, not a token bucket - a stateless single atomic upsert maps cleanly onto a
// fixed window (reset the count when the window rolls over, increment otherwise), whereas a token
// bucket's continuous refill doesn't translate to one SQL statement as naturally. Trade-off: allows a
// brief burst at window boundaries (same class of imprecision Bucket4j's own fixed-window algorithm
// has), acceptable here.
public class RateLimitRepository {

    private final DSLContext dsl;
    private final Clock clock;

    public RateLimitRepository(DSLContext dsl) {
        this(dsl, Clock.systemUTC());
    }

    // Clock is injectable so tests can fix "now" instead of racing the real wall clock - without
    // this, a test asserting "N+1th request in the same window is rejected" is intrinsically flaky:
    // it fails whenever the real second ticks over between the Nth and (N+1)th call, silently
    // starting a fresh window mid-test.
    public RateLimitRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    public boolean tryConsume(String clientIp, int limit) {
        OffsetDateTime windowStart = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        Integer requestCount = dsl.insertInto(RATE_LIMIT_BUCKET)
                .set(RATE_LIMIT_BUCKET.CLIENT_IP, clientIp)
                .set(RATE_LIMIT_BUCKET.WINDOW_START, windowStart)
                .set(RATE_LIMIT_BUCKET.REQUEST_COUNT, 1)
                .onConflict(RATE_LIMIT_BUCKET.CLIENT_IP)
                .doUpdate()
                .set(RATE_LIMIT_BUCKET.REQUEST_COUNT,
                        DSL.when(RATE_LIMIT_BUCKET.WINDOW_START.eq(windowStart), RATE_LIMIT_BUCKET.REQUEST_COUNT.plus(1))
                                .otherwise(1))
                .set(RATE_LIMIT_BUCKET.WINDOW_START, windowStart)
                .returning(RATE_LIMIT_BUCKET.REQUEST_COUNT)
                .fetchOne(RATE_LIMIT_BUCKET.REQUEST_COUNT);

        return requestCount != null && requestCount <= limit;
    }

    // Run periodically by ScheduledCleanupJobs so this table doesn't grow forever with one row per
    // IP that's ever made a request. A bucket's window is always the current second while active, so
    // anything more than a few minutes old is long stale.
    public void deleteStale() {
        dsl.deleteFrom(RATE_LIMIT_BUCKET)
                .where(RATE_LIMIT_BUCKET.WINDOW_START.lt(OffsetDateTime.now(clock).minusMinutes(5)))
                .execute();
    }
}
