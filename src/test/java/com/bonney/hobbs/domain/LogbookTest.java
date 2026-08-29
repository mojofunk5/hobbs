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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogbookTest {

    @Mock
    FlightEntryRepository flightEntryRepository;

    @Mock
    AircraftRepository aircraftRepository;

    Logbook logbook;

    @BeforeEach
    void setUp() {
        logbook = new Logbook(flightEntryRepository, aircraftRepository);
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
    void createAircraftSavesAndReturnsANewAircraft() {
        Aircraft aircraft = logbook.createAircraft("G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);

        assertThat(aircraft.getRegistration(), is("G-ABCD"));
        verify(aircraftRepository).save(aircraft);
    }

    @Test
    void listAircraftDelegatesToTheRepository() {
        List<Aircraft> expected = List.of(new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152",
                EngineCategory.SINGLE_ENGINE));
        when(aircraftRepository.findAll()).thenReturn(expected);

        List<Aircraft> result = logbook.listAircraft();

        assertThat(result, is(expected));
    }

    private FlightEntry aFlightEntry(FlightEntryId id) {
        return new FlightEntry(id, PilotId.random(), AircraftId.random(), null, LocalDate.now(),
                "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), PilotId.random(), null,
                45, 0, 45, 0, 0, 0, 45, 0, 0, 0, 1, 0, null);
    }
}
