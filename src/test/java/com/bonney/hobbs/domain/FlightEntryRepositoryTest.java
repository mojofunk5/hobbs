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
    private PilotId instructorPilotId;
    private AircraftId aircraftId;
    private AirfieldId airfieldId;
    private AirfieldId secondAirfieldId;
    private AirfieldId thirdAirfieldId;

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

        Pilot instructor = new Pilot(PilotId.random(), "Instructor Smith", null);
        new PilotRepository(dsl).save(instructor);
        instructorPilotId = instructor.getId();

        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE,
                null, null, null, null, null, null, null, null);
        new AircraftRepository(dsl).save(aircraft);
        aircraftId = aircraft.getId();

        AirfieldRepository airfieldRepository = new AirfieldRepository(dsl);
        Airfield airfield = new Airfield(AirfieldId.random(), "EGCM", "Manchester Barton Airport", "Manchester",
                "GB", "GB-ENG", 53.4694, -2.3803, 79, "small_airport", "ourairports", "1");
        airfieldRepository.save(airfield);
        airfieldId = airfield.getId();

        Airfield secondAirfield = new Airfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield",
                "Sherburn-in-Elmet", "GB", "GB-ENG", 53.7883, -1.2225, 26, "small_airport", "ourairports", "2");
        airfieldRepository.save(secondAirfield);
        secondAirfieldId = secondAirfield.getId();

        Airfield thirdAirfield = new Airfield(AirfieldId.random(), "EGNM", "Leeds Bradford Airport", "Leeds",
                "GB", "GB-ENG", 53.8659, -1.6606, 681, "medium_airport", "ourairports", "3");
        airfieldRepository.save(thirdAirfield);
        thirdAirfieldId = thirdAirfield.getId();
    }

    @Test
    void saveThenFindByIdRoundTripsAllFields() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        OffsetDateTime departureTime = OffsetDateTime.parse("2026-08-24T10:00:00Z");
        OffsetDateTime arrivalTime = OffsetDateTime.parse("2026-08-24T10:45:00Z");
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, null, date,
                "EGCM", departureTime, "EGCM", arrivalTime, null, null, instructorPilotId, null,
                45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits");

        repository.save(entry);

        FlightEntry found = repository.findById(entry.getId()).orElseThrow();
        assertThat(found, is(entry));
        assertThat(found.getDeparturePlace(), is("EGCM"));
        assertThat(found.getPilotInCommandId(), is(instructorPilotId));
        assertThat(found.getCoPilotId(), is(Optional.empty()));
        assertThat(found.getTotalMinutes(), is(45));
        assertThat(found.getDualMinutes(), is(45));
        assertThat(found.getDayLandings(), is(3));
        assertThat(found.getRemarks(), is("Circuits"));
        assertThat(found.getFlightTrackId(), is(Optional.empty()));
        assertThat(found.getDepartureAirfieldId(), is(Optional.empty()));
        assertThat(found.getArrivalAirfieldId(), is(Optional.empty()));
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
                LocalDate.now(), "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), null, null, pilotId,
                null, 30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
        repository.save(entry);

        FlightEntry found = repository.findById(entry.getId()).orElseThrow();
        assertThat(found.getFlightTrackId(), is(Optional.of(track.getId())));
    }

    @Test
    void departureAndArrivalAirfieldIdRoundTripWhenSet() {
        // Expand step of the departurePlace/arrivalPlace -> AirfieldId migration (see
        // docs/plans/airfield-picker.md) - a newly-created entry can reference a real Airfield row
        // even though existing rows never will (no backfill).
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, null, LocalDate.now(),
                "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), airfieldId, airfieldId, pilotId, null,
                30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);

        repository.save(entry);

        FlightEntry found = repository.findById(entry.getId()).orElseThrow();
        assertThat(found.getDepartureAirfieldId(), is(Optional.of(airfieldId)));
        assertThat(found.getArrivalAirfieldId(), is(Optional.of(airfieldId)));
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
                LocalDate.now(), "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), null, null,
                otherPilot.getId(), null, 30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
        repository.save(othersEntry);

        List<FlightEntry> found = repository.findAllByPilotId(otherPilot.getId());

        assertThat(found.stream().map(FlightEntry::getId).toList(), contains(othersEntry.getId()));
    }

    @Test
    void findRecentAirfieldIdsReturnsDistinctIdsMostRecentlyFlownFirst() {
        // Most recent flight is Manchester -> Sherburn; oldest is Sherburn -> Leeds.
        repository.save(entryWithAirfields(LocalDate.of(2026, 8, 1), "2026-08-01T09:00:00Z",
                secondAirfieldId, thirdAirfieldId));
        repository.save(entryWithAirfields(LocalDate.of(2026, 8, 24), "2026-08-24T09:00:00Z",
                airfieldId, secondAirfieldId));

        List<AirfieldId> recent = repository.findRecentAirfieldIds(pilotId, 5);

        assertThat(recent, contains(airfieldId, secondAirfieldId, thirdAirfieldId));
    }

    @Test
    void findRecentAirfieldIdsDoesNotCountTheSameAirfieldTwiceForRepeatedCircuits() {
        // A pilot who's flown the same airfield ten times in a row shouldn't crowd out everywhere
        // else they've been - this counts distinct airfields, not flights.
        for (int i = 0; i < 3; i++) {
            repository.save(entryWithAirfields(LocalDate.of(2026, 8, 20 + i), "2026-08-2" + i + "T09:00:00Z",
                    airfieldId, airfieldId));
        }
        repository.save(entryWithAirfields(LocalDate.of(2026, 8, 1), "2026-08-01T09:00:00Z",
                secondAirfieldId, thirdAirfieldId));

        List<AirfieldId> recent = repository.findRecentAirfieldIds(pilotId, 5);

        assertThat(recent, contains(airfieldId, secondAirfieldId, thirdAirfieldId));
    }

    @Test
    void findRecentAirfieldIdsIsCappedAtTheGivenLimit() {
        repository.save(entryWithAirfields(LocalDate.of(2026, 8, 24), "2026-08-24T09:00:00Z",
                airfieldId, secondAirfieldId));
        repository.save(entryWithAirfields(LocalDate.of(2026, 8, 1), "2026-08-01T09:00:00Z",
                secondAirfieldId, thirdAirfieldId));

        List<AirfieldId> recent = repository.findRecentAirfieldIds(pilotId, 2);

        assertThat(recent, contains(airfieldId, secondAirfieldId));
    }

    @Test
    void findRecentAirfieldIdsIgnoresEntriesWithNoAirfieldIdSet() {
        // Every entry from before the chunk 4 migration landed - no backfill, per
        // docs/plans/airfield-picker.md's Open questions and docs/DECISIONS.md.
        repository.save(anEntry(LocalDate.now(), "2026-08-24T10:00:00Z", "2026-08-24T10:30:00Z"));

        assertThat(repository.findRecentAirfieldIds(pilotId, 5), is(List.of()));
    }

    @Test
    void findRecentAirfieldIdsIsScopedToTheGivenPilot() {
        Pilot otherPilot = new Pilot(PilotId.random(), "Someone Else", null);
        new PilotRepository(dsl).save(otherPilot);
        FlightEntry othersEntry = new FlightEntry(FlightEntryId.random(), otherPilot.getId(), aircraftId, null,
                LocalDate.now(), "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), airfieldId,
                secondAirfieldId, otherPilot.getId(), null, 30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
        repository.save(othersEntry);

        assertThat(repository.findRecentAirfieldIds(pilotId, 5), is(List.of()));
    }

    private FlightEntry entryWithAirfields(LocalDate date, String departureTime, AirfieldId departureAirfieldId,
                                            AirfieldId arrivalAirfieldId) {
        return new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, null, date, "PLACE",
                OffsetDateTime.parse(departureTime), "PLACE", OffsetDateTime.parse(departureTime).plusMinutes(30),
                departureAirfieldId, arrivalAirfieldId, pilotId, null, 30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
    }

    private FlightEntry anEntry(LocalDate date, String departureTime, String arrivalTime) {
        return new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, null, date, "EGCM",
                OffsetDateTime.parse(departureTime), "EGCM", OffsetDateTime.parse(arrivalTime), null, null, pilotId,
                null, 30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
    }
}
