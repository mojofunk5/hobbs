package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.domain.HolderOperatingCapacity;
import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.dto.AirfieldDto;
import com.bonney.hobbs.dto.CreateFlightEntryDto;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import com.bonney.hobbs.dto.FlightEntryContextDto;
import com.bonney.hobbs.dto.PilotSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class FlightEntryContextEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aggregatesRecentAirfieldsRecentAircraftAndKnownPilotsForTheCaller() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        UUID zulu = seedAirfield("EGZZ", "Zulu Airfield");
        UUID pilotInCommandId = pilot.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        pilot.createFlightEntry(new CreateFlightEntryDto(aircraftId, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                zulu, zulu, pilotInCommandId, null, HolderOperatingCapacity.PILOT_IN_COMMAND, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        FlightEntryContextDto context = pilot.flightEntryContext();

        assertThat(context.getRecentAirfields().stream().map(AirfieldDto::getId).toList(), contains(zulu));
        assertThat(context.getRecentAircraft().stream().map(AircraftDto::getId).toList(), contains(aircraftId));
        assertThat(context.getKnownPilots().stream().map(PilotSummaryDto::getName).toList(),
                containsInAnyOrder("testuser", "Instructor Smith"));
    }

    @Test
    void matchesTheIndividualEndpointsResultsExactly() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        UUID zulu = seedAirfield("EGZZ", "Zulu Airfield");
        UUID pilotInCommandId = pilot.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        pilot.createFlightEntry(new CreateFlightEntryDto(aircraftId, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                zulu, zulu, pilotInCommandId, null, HolderOperatingCapacity.PILOT_IN_COMMAND, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        FlightEntryContextDto context = pilot.flightEntryContext();

        assertThat(context.getRecentAirfields().stream().map(AirfieldDto::getId).toList(),
                is(pilot.recentAirfields().stream().map(AirfieldDto::getId).toList()));
        assertThat(context.getRecentAircraft().stream().map(AircraftDto::getId).toList(),
                is(pilot.recentAircraft().stream().map(AircraftDto::getId).toList()));
        assertThat(context.getKnownPilots().stream().map(PilotSummaryDto::getId).toList(),
                is(pilot.searchPilots().stream().map(PilotSummaryDto::getId).toList()));
    }

    @Test
    void returnsEmptyListsWhenTheCallerHasNoHistory() {
        HobbsClient pilot = createAuthenticatedClient();

        FlightEntryContextDto context = pilot.flightEntryContext();

        assertThat(context.getRecentAirfields(), is(List.of()));
        assertThat(context.getRecentAircraft(), is(List.of()));
        // knownPilots always includes the caller themselves, same as GET /pilot?search=.
        assertThat(context.getKnownPilots().stream().map(PilotSummaryDto::getName).toList(), contains("testuser"));
    }

    @Test
    void resultsAreScopedToTheCallingPilot() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        UUID zulu = seedAirfield("EGZZ", "Zulu Airfield");
        UUID pilotInCommandId = first.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        first.createFlightEntry(new CreateFlightEntryDto(aircraftId, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                zulu, zulu, pilotInCommandId, null, HolderOperatingCapacity.PILOT_IN_COMMAND, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        FlightEntryContextDto context = second.flightEntryContext();

        assertThat(context.getRecentAirfields(), is(List.of()));
        assertThat(context.getRecentAircraft(), is(List.of()));
        assertThat(context.getKnownPilots().stream().map(PilotSummaryDto::getName).toList(), contains("testuser"));
    }
}
