package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.FlightEntryDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightEntryEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aPilotCanLogAFlightAndReadItBack() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");

        FlightEntryDto created = pilot.createFlightEntry(aFlightEntry(pilot, aircraftId, null));

        assertThat(created.getDepartureAirfieldId(), is(notNullValue()));
        assertThat(created.getTotalMinutes(), is(45));
        assertThat(created.getFlightTrackId(), is((UUID) null));
        assertThat(created.getHolderOperatingCapacity(), is("PILOT_UNDER_TRAINING"));
        assertThat(created.getHolderOperatingCapacityNotation(), is("P.u/t"));

        FlightEntryDto fetched = pilot.getFlightEntry(created.getId());
        assertThat(fetched.getId(), is(created.getId()));
        assertThat(fetched.getHolderOperatingCapacityNotation(), is("P.u/t"));
    }

    @Test
    void listFlightEntriesOnlyReturnsTheAuthenticatedPilotsOwnEntries() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        FlightEntryDto firstEntry = first.createFlightEntry(aFlightEntry(first, aircraftId, null));
        seedAircraft("G-WXYZ", "Piper", "PA-28");

        List<FlightEntryDto> firstList = first.listFlightEntries();
        List<FlightEntryDto> secondList = second.listFlightEntries();

        assertThat(firstList.stream().map(FlightEntryDto::getId).toList(), contains(firstEntry.getId()));
        assertThat(secondList, is(List.of()));
    }

    @Test
    void aPilotCannotFetchAnotherPilotsFlightEntry() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
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
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");

        FlightEntryDto created = pilot.createFlightEntry(aFlightEntry(pilot, aircraftId, null));

        assertThat(created.getFlightTrackId(), is((UUID) null));
    }
}
