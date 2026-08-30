package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.AircraftDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;

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
}
