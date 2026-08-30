package com.bonney.hobbs.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogbookTest {

    @Mock
    FlightEntryRepository flightEntryRepository;

    @Mock
    AircraftRepository aircraftRepository;

    @Mock
    AirfieldRepository airfieldRepository;

    Logbook logbook;

    @BeforeEach
    void setUp() {
        logbook = new Logbook(flightEntryRepository, aircraftRepository, airfieldRepository);
    }

    @Test
    void createEntrySavesAndReturnsANewFlightEntryWithNoTrack() {
        PilotId pilotId = PilotId.random();
        AircraftId aircraftId = AircraftId.random();
        LocalDate date = LocalDate.of(2026, 8, 24);
        OffsetDateTime departureTime = OffsetDateTime.now();
        OffsetDateTime arrivalTime = departureTime.plusMinutes(45);

        FlightEntry entry = logbook.createEntry(pilotId, aircraftId, null, date, "EGCM", departureTime,
                "EGCM", arrivalTime, PilotId.random(), null, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits");

        assertThat(entry.getPilotId(), is(pilotId));
        assertThat(entry.getAircraftId(), is(aircraftId));
        assertThat(entry.getFlightTrackId(), is(Optional.empty()));
        assertThat(entry.getTotalMinutes(), is(45));

        ArgumentCaptor<FlightEntry> captor = ArgumentCaptor.forClass(FlightEntry.class);
        verify(flightEntryRepository).save(captor.capture());
        assertThat(captor.getValue(), sameInstance(entry));
    }

    @Test
    void createEntryCarriesAnOptionalFlightTrackId() {
        FlightTrackId trackId = FlightTrackId.random();

        FlightEntry entry = logbook.createEntry(PilotId.random(), AircraftId.random(), trackId,
                LocalDate.now(), "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), PilotId.random(),
                null, 45, 0, 45, 0, 0, 0, 45, 0, 0, 0, 2, 0, null);

        assertThat(entry.getFlightTrackId(), is(Optional.of(trackId)));
    }

    @Test
    void getDelegatesToTheRepository() {
        FlightEntryId id = FlightEntryId.random();
        FlightEntry expected = aFlightEntry(id);
        when(flightEntryRepository.findById(id)).thenReturn(Optional.of(expected));

        Optional<FlightEntry> result = logbook.get(id);

        assertThat(result, is(Optional.of(expected)));
    }

    @Test
    void listForPilotDelegatesToTheRepository() {
        PilotId pilotId = PilotId.random();
        List<FlightEntry> expected = List.of(aFlightEntry(FlightEntryId.random()));
        when(flightEntryRepository.findAllByPilotId(pilotId)).thenReturn(expected);

        List<FlightEntry> result = logbook.listForPilot(pilotId);

        assertThat(result, is(expected));
    }

    @Test
    void searchAircraftDelegatesToTheFullSearchByDefault() {
        List<Aircraft> expected = List.of(new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152",
                EngineCategory.SINGLE_ENGINE, null, null, null, null, null, null, null, null));
        when(aircraftRepository.search("abcd", 50)).thenReturn(expected);

        List<Aircraft> result = logbook.searchAircraft("abcd", false);

        assertThat(result, is(expected));
    }

    @Test
    void searchAircraftDelegatesToRegistrationOnlySearchWhenRequested() {
        List<Aircraft> expected = List.of(new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152",
                EngineCategory.SINGLE_ENGINE, null, null, null, null, null, null, null, null));
        when(aircraftRepository.searchByRegistration("abcd", 50)).thenReturn(expected);

        List<Aircraft> result = logbook.searchAircraft("abcd", true);

        assertThat(result, is(expected));
    }

    @Test
    void searchAircraftRejectsASearchShorterThanTheMinimumLength() {
        assertThrows(InvalidAircraftSearchException.class, () -> logbook.searchAircraft("a", false));
    }

    @Test
    void searchAircraftRejectsANullSearch() {
        assertThrows(InvalidAircraftSearchException.class, () -> logbook.searchAircraft(null, false));
    }

    @Test
    void searchAirfieldsDelegatesToTheRepositorySearchWhenSearchIsGiven() {
        List<Airfield> expected = List.of(anAirfield());
        when(airfieldRepository.search("sherburn")).thenReturn(expected);

        List<Airfield> result = logbook.searchAirfields("sherburn");

        assertThat(result, is(expected));
    }

    @Test
    void searchAirfieldsReturnsFindAllWhenSearchIsNullOrBlank() {
        List<Airfield> expected = List.of(anAirfield());
        when(airfieldRepository.findAll()).thenReturn(expected);

        assertThat(logbook.searchAirfields(null), is(expected));
        assertThat(logbook.searchAirfields(""), is(expected));
        assertThat(logbook.searchAirfields("   "), is(expected));
    }

    private Airfield anAirfield() {
        return new Airfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield", "Sherburn-in-Elmet", "GB",
                "GB-ENG", 53.7883, -1.2225, 26, "small_airport", "ourairports", "12345");
    }

    private FlightEntry aFlightEntry(FlightEntryId id) {
        return new FlightEntry(id, PilotId.random(), AircraftId.random(), null, LocalDate.now(),
                "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), PilotId.random(), null,
                45, 0, 45, 0, 0, 0, 45, 0, 0, 0, 1, 0, null);
    }
}
