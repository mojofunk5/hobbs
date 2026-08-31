package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.domain.HolderOperatingCapacity;
import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.dto.CreateFlightEntryDto;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AircraftEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anAuthenticatedPilotCanSearchAircraftByRegistrationMakeOrModel() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID id = seedAircraft("G-ABCD", "Cessna", "152");

        assertThat(pilot.searchAircraft("ABCD").stream().map(AircraftDto::getId).toList(), contains(id));
        assertThat(pilot.searchAircraft("cess").stream().map(AircraftDto::getId).toList(), contains(id));
        assertThat(pilot.searchAircraft("152").stream().map(AircraftDto::getId).toList(), contains(id));
    }

    @Test
    void aircraftIsSharedAcrossPilotsNotScopedToOneAccount() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID id = seedAircraft("G-SHRD", "Piper", "PA-28");

        assertThat(second.searchAircraft("G-SHRD").stream().map(AircraftDto::getId).toList(), contains(id));
    }

    @Test
    void searchWithNoMatchesReturnsAnEmptyList() {
        HobbsClient pilot = createAuthenticatedClient();
        seedAircraft("G-ABCD", "Cessna", "152");

        assertThat(pilot.searchAircraft("nomatch"), is(java.util.List.of()));
    }

    @Test
    void searchShorterThanTwoCharactersIsRejected() {
        HobbsClient pilot = createAuthenticatedClient();

        assertThrows(FeignException.BadRequest.class, () -> pilot.searchAircraft("a"));
    }

    @Test
    void missingSearchIsRejected() {
        HobbsClient pilot = createAuthenticatedClient();

        assertThrows(FeignException.BadRequest.class, () -> pilot.searchAircraft(null));
    }

    @Test
    void searchIsCaseInsensitiveAndMatchesMultipleAircraft() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID cessna = seedAircraft("G-ABCD", "Cessna", "152");
        UUID cessnaTwo = seedAircraft("G-WXYZ", "cessna", "172");
        seedAircraft("G-PIPR", "Piper", "PA-28");

        assertThat(pilot.searchAircraft("CESSNA").stream().map(AircraftDto::getId).toList(),
                containsInAnyOrder(cessna, cessnaTwo));
    }

    @Test
    void registrationOnlySearchDoesNotMatchMakeOrModel() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID id = seedAircraft("G-ABCD", "Cessna", "152");
        seedAircraft("G-WXYZ", "Piper", "Warrior");

        assertThat(pilot.searchAircraft("ABCD", true).stream().map(AircraftDto::getId).toList(), contains(id));
        assertThat(pilot.searchAircraft("warrior", true), is(java.util.List.of()));
    }

    @Test
    void registrationOnlyFalseStillMatchesMakeOrModel() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID id = seedAircraft("G-ABCD", "Cessna", "152");

        assertThat(pilot.searchAircraft("cess", false).stream().map(AircraftDto::getId).toList(), contains(id));
    }

    @Test
    void recentReturnsTheCallersRecentlyFlownAircraftMostRecentFirst() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID airfieldId = seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");
        UUID cessna = seedAircraft("G-ABCD", "Cessna", "152");
        UUID warrior = seedAircraft("G-EFGH", "Piper", "Warrior");
        UUID pilotInCommandId = pilot.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        pilot.createFlightEntry(new CreateFlightEntryDto(cessna, null, LocalDate.of(2026, 8, 1),
                OffsetDateTime.parse("2026-08-01T10:00:00Z"), OffsetDateTime.parse("2026-08-01T10:45:00Z"),
                airfieldId, airfieldId, pilotInCommandId, null, HolderOperatingCapacity.PILOT_IN_COMMAND, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));
        pilot.createFlightEntry(new CreateFlightEntryDto(warrior, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                airfieldId, airfieldId, pilotInCommandId, null, HolderOperatingCapacity.PILOT_IN_COMMAND, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        List<UUID> ids = pilot.recentAircraft().stream().map(AircraftDto::getId).toList();

        assertThat(ids, contains(warrior, cessna));
    }

    @Test
    void recentReturnsAnEmptyListWhenTheCallerHasNoFlightEntries() {
        HobbsClient pilot = createAuthenticatedClient();
        seedAircraft("G-ABCD", "Cessna", "152");

        assertThat(pilot.recentAircraft(), is(java.util.List.of()));
    }

    @Test
    void recentIsScopedToTheCallingPilot() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID airfieldId = seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");
        UUID cessna = seedAircraft("G-ABCD", "Cessna", "152");
        UUID pilotInCommandId = first.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        first.createFlightEntry(new CreateFlightEntryDto(cessna, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                airfieldId, airfieldId, pilotInCommandId, null, HolderOperatingCapacity.PILOT_IN_COMMAND, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        assertThat(second.recentAircraft(), is(java.util.List.of()));
    }
}
