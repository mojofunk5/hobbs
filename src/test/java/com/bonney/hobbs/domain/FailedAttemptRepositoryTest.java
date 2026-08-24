package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

// Concurrency correctness is deliberately not asserted here, same reasoning as
// RateLimitRepositoryTest - the atomic upsert this mirrors is verified against real Postgres, not
// H2, which has a known gap with RETURNING on a conflict-triggered UPDATE under concurrent access.
class FailedAttemptRepositoryTest {

    private FailedAttemptRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new FailedAttemptRepository(dsl);
    }

    @Test
    void aKeyWithNoRecordedFailuresIsNotThrottled() {
        assertThat(repository.isThrottled("alice@example.com", FailedAttemptPurpose.LOGIN, 5, Duration.ofMinutes(15)), is(false));
    }

    @Test
    void isNotThrottledWhileUnderTheLimit() {
        for (int i = 0; i < 4; i++) {
            repository.recordFailure("alice@example.com", FailedAttemptPurpose.LOGIN, Duration.ofMinutes(15));
        }

        assertThat(repository.isThrottled("alice@example.com", FailedAttemptPurpose.LOGIN, 5, Duration.ofMinutes(15)), is(false));
    }

    @Test
    void isThrottledOnceTheLimitIsReached() {
        for (int i = 0; i < 5; i++) {
            repository.recordFailure("alice@example.com", FailedAttemptPurpose.LOGIN, Duration.ofMinutes(15));
        }

        assertThat(repository.isThrottled("alice@example.com", FailedAttemptPurpose.LOGIN, 5, Duration.ofMinutes(15)), is(true));
    }

    @Test
    void differentKeysHaveIndependentLimits() {
        for (int i = 0; i < 5; i++) {
            repository.recordFailure("alice@example.com", FailedAttemptPurpose.LOGIN, Duration.ofMinutes(15));
        }

        assertThat(repository.isThrottled("bob@example.com", FailedAttemptPurpose.LOGIN, 5, Duration.ofMinutes(15)), is(false));
    }

    @Test
    void differentPurposesForTheSameKeyHaveIndependentLimits() {
        for (int i = 0; i < 5; i++) {
            repository.recordFailure("alice@example.com", FailedAttemptPurpose.LOGIN, Duration.ofMinutes(15));
        }

        assertThat(repository.isThrottled("alice@example.com", FailedAttemptPurpose.PASSWORD_RESET, 5, Duration.ofMinutes(15)), is(false));
    }

    @Test
    void deleteStaleDoesNotRemoveAFreshRow() {
        repository.recordFailure("alice@example.com", FailedAttemptPurpose.LOGIN, Duration.ofMinutes(15));

        repository.deleteStale();

        // A just-recorded failure is nowhere near the one-hour staleness threshold, so it survives.
        assertThat(repository.isThrottled("alice@example.com", FailedAttemptPurpose.LOGIN, 1, Duration.ofMinutes(15)), is(true));
    }
}
