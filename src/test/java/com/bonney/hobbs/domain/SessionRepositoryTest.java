package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.bonney.hobbs.TestPilots.randomPilot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SessionRepositoryTest {

    private SessionRepository repository;
    private PilotId pilotId;
    private PilotId otherPilotId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new SessionRepository(dsl);

        PilotRepository pilotRepository = new PilotRepository(dsl);
        Pilot pilot = randomPilot();
        pilotRepository.save(pilot);
        pilotId = pilot.getId();
        Pilot otherPilot = randomPilot();
        pilotRepository.save(otherPilot);
        otherPilotId = otherPilot.getId();
    }

    @Test
    void aFreshSessionIsFoundAndReturnsThePilotId() {
        SessionId id = SessionId.random();
        repository.save(id, pilotId, OffsetDateTime.now());

        Optional<PilotId> found = repository.findIfUnexpiredAndTouch(id, 24);

        assertThat(found, is(Optional.of(pilotId)));
    }

    @Test
    void aSessionOlderThanTheTtlIsNotFound() {
        SessionId id = SessionId.random();
        repository.save(id, pilotId, OffsetDateTime.now().minusHours(25));

        assertThat(repository.findIfUnexpiredAndTouch(id, 24), is(Optional.empty()));
    }

    @Test
    void aSessionWithinTheTtlIsStillFound() {
        SessionId id = SessionId.random();
        repository.save(id, pilotId, OffsetDateTime.now().minusHours(23));

        assertThat(repository.findIfUnexpiredAndTouch(id, 24), is(Optional.of(pilotId)));
    }

    @Test
    void aValidLookupBumpsLastAccessedSoTheSessionStaysAliveOnSlidingWindow() {
        SessionId id = SessionId.random();
        // Just inside the TTL - if the lookup below didn't slide the window forward, a later lookup
        // against the original timestamp would already be expired.
        repository.save(id, pilotId, OffsetDateTime.now().minusHours(23).minusMinutes(59));

        Optional<PilotId> firstLookup = repository.findIfUnexpiredAndTouch(id, 24);
        Optional<PilotId> secondLookup = repository.findIfUnexpiredAndTouch(id, 24);

        assertThat(firstLookup, is(Optional.of(pilotId)));
        assertThat(secondLookup, is(Optional.of(pilotId)));
    }

    @Test
    void unknownSessionIsNotFound() {
        assertThat(repository.findIfUnexpiredAndTouch(SessionId.random(), 24), is(Optional.empty()));
    }

    @Test
    void deleteAllForPilotRemovesOnlyThatPilotsSessions() {
        SessionId mineId = SessionId.random();
        SessionId otherId = SessionId.random();
        repository.save(mineId, pilotId, OffsetDateTime.now());
        repository.save(otherId, otherPilotId, OffsetDateTime.now());

        repository.deleteAllForPilot(pilotId);

        assertThat(repository.findIfUnexpiredAndTouch(mineId, 24), is(Optional.empty()));
        assertThat(repository.findIfUnexpiredAndTouch(otherId, 24), is(Optional.of(otherPilotId)));
    }

    @Test
    void deleteExpiredRemovesOnlySessionsOlderThanTheTtl() {
        SessionId expiredId = SessionId.random();
        SessionId freshId = SessionId.random();
        repository.save(expiredId, pilotId, OffsetDateTime.now().minusHours(25));
        repository.save(freshId, pilotId, OffsetDateTime.now());

        repository.deleteExpired(24);

        assertThat(repository.findIfUnexpiredAndTouch(expiredId, 1000), is(Optional.empty()));
        assertThat(repository.findIfUnexpiredAndTouch(freshId, 24), is(Optional.of(pilotId)));
    }
}
