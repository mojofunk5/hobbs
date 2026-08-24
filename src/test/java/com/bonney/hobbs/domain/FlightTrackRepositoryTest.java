package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FlightTrackRepositoryTest {

    private FlightTrackRepository repository;
    private PilotId pilotId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new FlightTrackRepository(dsl);

        Pilot pilot = new Pilot(PilotId.random(), "William", "william@example.com");
        new PilotRepository(dsl).save(pilot);
        pilotId = pilot.getId();
    }

    @Test
    void aTrackCanBeSavedWhileStillRecordingWithNoEndTimeYet() {
        FlightTrack track = new FlightTrack(FlightTrackId.random(), pilotId, OffsetDateTime.now(), null,
                "[{\"t\":0,\"lat\":53.8,\"lon\":-1.4}]");

        repository.save(track);

        FlightTrack found = repository.findById(track.getId()).orElseThrow();
        assertThat(found.getEndedAt(), is(Optional.empty()));
        assertThat(found.getPointsJson(), is(track.getPointsJson()));
    }

    @Test
    void savingAgainWithAnEndTimeUpdatesTheExistingRow() {
        // Truncated to microseconds because that's the real precision TIMESTAMP WITH TIME ZONE
        // stores - OffsetDateTime.now() carries nanosecond precision on this JVM, so comparing an
        // untruncated value against one that's been through a save/load round trip is intrinsically
        // flaky: it only fails when now()'s last three nanosecond digits aren't already zero, which
        // happens most of the time but not always. Flight-track timestamps never need sub-microsecond
        // precision in practice, so truncating the test's expectation to match what actually survives
        // the round trip is the correct fix, not a workaround.
        FlightTrackId id = FlightTrackId.random();
        OffsetDateTime startedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        repository.save(new FlightTrack(id, pilotId, startedAt, null, "[]"));

        OffsetDateTime endedAt = startedAt.plusMinutes(45);
        repository.save(new FlightTrack(id, pilotId, startedAt, endedAt, "[{\"t\":2700}]"));

        FlightTrack found = repository.findById(id).orElseThrow();
        assertThat(found.getEndedAt(), is(Optional.of(endedAt)));
        assertThat(found.getPointsJson(), is("[{\"t\":2700}]"));
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertThat(repository.findById(FlightTrackId.random()), is(Optional.empty()));
    }
}
