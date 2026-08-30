package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.CreateAircraftDto;
import com.bonney.hobbs.dto.FlightEntryDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightEntryEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aPilotCanLogAFlightAndReadItBack() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID aircraftId = pilot.createAircraft(new CreateAircraftDto("G-ABCD", "Cessna", "152", "SINGLE_ENGINE")).getId();

        FlightEntryDto created = pilot.createFlightEntry(aFlightEntry(pilot, aircraftId, null));

        assertThat(created.getDeparturePlace(), is("EGCM"));
        assertThat(created.getTotalMinutes(), is(45));
        assertThat(created.getFlightTrackId(), is((UUID) null));

        FlightEntryDto fetched = pilot.getFlightEntry(created.getId());
        assertThat(fetched.getId(), is(created.getId()));
    }

    @Test
    void listFlightEntriesOnlyReturnsTheAuthenticatedPilotsOwnEntries() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID aircraftId = first.createAircraft(new CreateAircraftDto("G-ABCD", "Cessna", "152", "SINGLE_ENGINE")).getId();
        FlightEntryDto firstEntry = first.createFlightEntry(aFlightEntry(first, aircraftId, null));
        second.createAircraft(new CreateAircraftDto("G-WXYZ", "Piper", "PA-28", "SINGLE_ENGINE"));

        List<FlightEntryDto> firstList = first.listFlightEntries();
        List<FlightEntryDto> secondList = second.listFlightEntries();

        assertThat(firstList.stream().map(FlightEntryDto::getId).toList(), contains(firstEntry.getId()));
        assertThat(secondList, is(List.of()));
    }

    @Test
    void aPilotCannotFetchAnotherPilotsFlightEntry() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID aircraftId = first.createAircraft(new CreateAircraftDto("G-ABCD", "Cessna", "152", "SINGLE_ENGINE")).getId();
        FlightEntryDto entry = first.createFlightEntry(aFlightEntry(first, aircraftId, null));

        assertThrows(FeignException.Forbidden.class, () -> second.getFlightEntry(entry.getId()));
    }

    @Test
    void fetchingAnUnknownFlightEntryReturnsNotFound() {
        HobbsClient pilot = createAuthenticatedClient();

        assertThrows(FeignException.NotFound.class, () -> pilot.getFlightEntry(UUID.randomUUID()));
    }

    @Test
    void aFlightEntryWithoutAFlightTrackIsJustAsValidAsOneWithOne() {
        // Manual entry is the primary path, GPS is an optional fast-path - see FlightEntry's Javadoc.
        // This is really the same assertion as aPilotCanLogAFlightAndReadItBack's null-track check,
        // named explicitly to keep that invariant visible as a deliberate contract, not incidental.
        HobbsClient pilot = createAuthenticatedClient();
        UUID aircraftId = pilot.createAircraft(new CreateAircraftDto("G-ABCD", "Cessna", "152", "SINGLE_ENGINE")).getId();

        FlightEntryDto created = pilot.createFlightEntry(aFlightEntry(pilot, aircraftId, null));

        assertThat(created.getFlightTrackId(), is((UUID) null));
    }
}
