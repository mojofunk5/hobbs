package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class FlightEntryRepositoryTest {

    private DSLContext dsl;
    private FlightEntryRepository repository;
    private PilotId pilotId;
    private AircraftId aircraftId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new FlightEntryRepository(dsl);

        // FlightEntry has FK constraints on pilot and aircraft - both need a real row to reference.
        Pilot pilot = new Pilot(PilotId.random(), "William", null);
        new PilotRepository(dsl).save(pilot);
        pilotId = pilot.getId();

        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);
        new AircraftRepository(dsl).save(aircraft);
        aircraftId = aircraft.getId();
    }

    @Test
    void saveThenFindByIdRoundTripsAllFields() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        OffsetDateTime departureTime = OffsetDateTime.parse("2026-08-24T10:00:00Z");
        OffsetDateTime arrivalTime = OffsetDateTime.parse("2026-08-24T10:45:00Z");
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, null, date,
                "EGCM", departureTime, "EGCM", arrivalTime, "Instructor Smith",
                45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits");

        repository.save(entry);

        FlightEntry found = repository.findById(entry.getId()).orElseThrow();
        assertThat(found, is(entry));
        assertThat(found.getDeparturePlace(), is("EGCM"));
        assertThat(found.getPicName(), is("Instructor Smith"));
        assertThat(found.getTotalMinutes(), is(45));
        assertThat(found.getDualMinutes(), is(45));
        assertThat(found.getDayLandings(), is(3));
        assertThat(found.getRemarks(), is("Circuits"));
        assertThat(found.getFlightTrackId(), is(Optional.empty()));
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertThat(repository.findById(FlightEntryId.random()), is(Optional.empty()));
    }

    @Test
    void aFlightTrackCanBeLinkedButIsNotRequired() {
        FlightTrack track = new FlightTrack(FlightTrackId.random(), pilotId, OffsetDateTime.now(), null, "[]");
        new FlightTrackRepository(dsl).save(track);

        FlightEntry entry = new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, track.getId(),
                LocalDate.now(), "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), "Self",
                30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
        repository.save(entry);

        FlightEntry found = repository.findById(entry.getId()).orElseThrow();
        assertThat(found.getFlightTrackId(), is(Optional.of(track.getId())));
    }

    @Test
    void findAllByPilotIdOrdersByDateThenDepartureTimeDescending() {
        FlightEntry older = anEntry(LocalDate.of(2026, 8, 1), "2026-08-01T09:00:00Z", "2026-08-01T09:30:00Z");
        FlightEntry sameDayEarlier = anEntry(LocalDate.of(2026, 8, 24), "2026-08-24T08:00:00Z", "2026-08-24T08:30:00Z");
        FlightEntry sameDayLater = anEntry(LocalDate.of(2026, 8, 24), "2026-08-24T14:00:00Z", "2026-08-24T14:45:00Z");
        repository.save(older);
        repository.save(sameDayEarlier);
        repository.save(sameDayLater);

        List<FlightEntry> found = repository.findAllByPilotId(pilotId);

        assertThat(found.stream().map(FlightEntry::getId).toList(),
                contains(sameDayLater.getId(), sameDayEarlier.getId(), older.getId()));
    }

    @Test
    void findAllByPilotIdOnlyReturnsThatPilotsEntries() {
        Pilot otherPilot = new Pilot(PilotId.random(), "Someone Else", null);
        new PilotRepository(dsl).save(otherPilot);
        repository.save(anEntry(LocalDate.now(), "2026-08-24T10:00:00Z", "2026-08-24T10:30:00Z"));
        FlightEntry othersEntry = new FlightEntry(FlightEntryId.random(), otherPilot.getId(), aircraftId, null,
                LocalDate.now(), "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), "Someone Else",
                30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
        repository.save(othersEntry);

        List<FlightEntry> found = repository.findAllByPilotId(otherPilot.getId());

        assertThat(found.stream().map(FlightEntry::getId).toList(), contains(othersEntry.getId()));
    }

    private FlightEntry anEntry(LocalDate date, String departureTime, String arrivalTime) {
        return new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, null, date, "EGCM",
                OffsetDateTime.parse(departureTime), "EGCM", OffsetDateTime.parse(arrivalTime), "Self",
                30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
    }
}
