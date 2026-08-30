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

        FlightEntry entry = logbook.createEntry(pilotId, aircraftId, null, date, departureTime,
                arrivalTime, AirfieldId.random(), AirfieldId.random(), PilotId.random(), null, 45, 0, 45, 0, 0, 0,
                0, 0, 45, 0, 3, 0, "Circuits");

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
                LocalDate.now(), OffsetDateTime.now(), OffsetDateTime.now(), AirfieldId.random(),
                AirfieldId.random(), PilotId.random(), null, 45, 0, 45, 0, 0, 0, 45, 0, 0, 0, 2, 0, null);

        assertThat(entry.getFlightTrackId(), is(Optional.of(trackId)));
    }

    @Test
    void createEntryCarriesTheGivenDepartureAndArrivalAirfieldIds() {
        AirfieldId departureAirfieldId = AirfieldId.random();
        AirfieldId arrivalAirfieldId = AirfieldId.random();

        FlightEntry entry = logbook.createEntry(PilotId.random(), AircraftId.random(), null,
                LocalDate.now(), OffsetDateTime.now(), OffsetDateTime.now(), departureAirfieldId,
                arrivalAirfieldId, PilotId.random(), null, 45, 0, 45, 0, 0, 0, 45, 0, 0, 0, 2, 0, null);

        assertThat(entry.getDepartureAirfieldId(), is(departureAirfieldId));
        assertThat(entry.getArrivalAirfieldId(), is(arrivalAirfieldId));
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
        PilotId callerId = PilotId.random();
        List<Airfield> expected = List.of(anAirfield());
        when(airfieldRepository.search("sherburn")).thenReturn(expected);
        when(flightEntryRepository.findRecentAirfieldIds(callerId, Logbook.RECENT_AIRFIELD_LIMIT)).thenReturn(List.of());

        List<Airfield> result = logbook.searchAirfields(callerId, "sherburn");

        assertThat(result, is(expected));
    }

    @Test
    void searchAirfieldsReturnsFindAllWhenSearchIsNullOrBlank() {
        PilotId callerId = PilotId.random();
        List<Airfield> expected = List.of(anAirfield());
        when(airfieldRepository.findAll()).thenReturn(expected);
        when(flightEntryRepository.findRecentAirfieldIds(callerId, Logbook.RECENT_AIRFIELD_LIMIT)).thenReturn(List.of());

        assertThat(logbook.searchAirfields(callerId, null), is(expected));
        assertThat(logbook.searchAirfields(callerId, ""), is(expected));
        assertThat(logbook.searchAirfields(callerId, "   "), is(expected));
    }

    @Test
    void searchAirfieldsPutsTheCallersRecentlyFlownAirfieldsFirst() {
        PilotId callerId = PilotId.random();
        Airfield sherburn = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield");
        Airfield alpha = anAirfield(AirfieldId.random(), "EGAA", "Alpha Airfield");
        Airfield leeds = anAirfield(AirfieldId.random(), "EGNM", "Leeds Bradford Airport");
        // Alphabetically alpha, leeds, sherburn - but sherburn was flown most recently.
        when(airfieldRepository.findAll()).thenReturn(List.of(alpha, leeds, sherburn));
        when(flightEntryRepository.findRecentAirfieldIds(callerId, Logbook.RECENT_AIRFIELD_LIMIT))
                .thenReturn(List.of(sherburn.getId()));

        List<Airfield> result = logbook.searchAirfields(callerId, null);

        assertThat(result, is(List.of(sherburn, alpha, leeds)));
    }

    @Test
    void searchAirfieldsIgnoresARecentAirfieldNotPresentInTheMatchedSet() {
        PilotId callerId = PilotId.random();
        Airfield alpha = anAirfield(AirfieldId.random(), "EGAA", "Alpha Airfield");
        AirfieldId notMatched = AirfieldId.random();
        when(airfieldRepository.search("alpha")).thenReturn(List.of(alpha));
        when(flightEntryRepository.findRecentAirfieldIds(callerId, Logbook.RECENT_AIRFIELD_LIMIT))
                .thenReturn(List.of(notMatched));

        List<Airfield> result = logbook.searchAirfields(callerId, "alpha");

        assertThat(result, is(List.of(alpha)));
    }

    private Airfield anAirfield() {
        return anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield");
    }

    private Airfield anAirfield(AirfieldId id, String icaoCode, String name) {
        return new Airfield(id, icaoCode, name, "Somewhere", "GB", "GB-ENG", 53.7883, -1.2225, 26,
                "small_airport", "ourairports", id.value().toString());
    }

    private FlightEntry aFlightEntry(FlightEntryId id) {
        return new FlightEntry(id, PilotId.random(), AircraftId.random(), null, LocalDate.now(),
                OffsetDateTime.now(), OffsetDateTime.now(), AirfieldId.random(), AirfieldId.random(),
                PilotId.random(), null, 45, 0, 45, 0, 0, 0, 45, 0, 0, 0, 1, 0, null);
    }
}
