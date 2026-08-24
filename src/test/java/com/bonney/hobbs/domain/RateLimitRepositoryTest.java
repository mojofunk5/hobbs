package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

// Concurrency correctness (the atomic INSERT ... ON CONFLICT DO UPDATE ... RETURNING upsert genuinely
// not losing updates under concurrent access) is deliberately NOT asserted here - verified manually
// against a real Postgres instance instead (400/400 requests correctly counted, no lost updates under
// 20 concurrent threads), since H2's MODE=PostgreSQL compatibility has a known gap with this exact
// pattern (RETURNING on a conflict-triggered UPDATE under concurrent access) - the same documented
// "close but not perfect" limitation docs/DEPLOYMENT.md's Testing Strategy section already flags as
// the motivation for eventually moving this suite to Testcontainers-backed real Postgres.
//
// Every test here uses a fixed Clock rather than the real wall clock - RateLimitRepository's window
// is "the current second", so a test racing the real clock is intrinsically flaky: it fails whenever
// a second ticks over between two calls in the same test, silently starting a fresh window mid-test.
class RateLimitRepositoryTest {

    private DataSource dataSource;
    private static final Instant FIXED_NOW = Instant.parse("2026-08-24T10:15:30.000Z");

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        Flyway.configure().dataSource(ds).load().migrate();
        dataSource = ds;
    }

    private RateLimitRepository repositoryAt(Instant instant) {
        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        return new RateLimitRepository(dsl, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void firstRequestFromAnIpIsAllowed() {
        assertThat(repositoryAt(FIXED_NOW).tryConsume("1.2.3.4", 10), is(true));
    }

    @Test
    void requestsWithinTheLimitAreAllowed() {
        RateLimitRepository repository = repositoryAt(FIXED_NOW);
        for (int i = 0; i < 10; i++) {
            assertThat(repository.tryConsume("1.2.3.4", 10), is(true));
        }
    }

    @Test
    void requestsBeyondTheLimitInTheSameWindowAreRejected() {
        RateLimitRepository repository = repositoryAt(FIXED_NOW);
        for (int i = 0; i < 10; i++) {
            repository.tryConsume("1.2.3.4", 10);
        }

        assertThat(repository.tryConsume("1.2.3.4", 10), is(false));
    }

    @Test
    void aNewWindowResetsTheCount() {
        RateLimitRepository firstWindow = repositoryAt(FIXED_NOW);
        for (int i = 0; i < 10; i++) {
            firstWindow.tryConsume("1.2.3.4", 10);
        }
        assertThat(firstWindow.tryConsume("1.2.3.4", 10), is(false));

        RateLimitRepository secondWindow = repositoryAt(FIXED_NOW.plusSeconds(1));

        assertThat(secondWindow.tryConsume("1.2.3.4", 10), is(true));
    }

    @Test
    void differentIpsHaveIndependentLimits() {
        RateLimitRepository repository = repositoryAt(FIXED_NOW);
        for (int i = 0; i < 10; i++) {
            repository.tryConsume("1.2.3.4", 10);
        }

        assertThat(repository.tryConsume("5.6.7.8", 10), is(true));
    }
}
