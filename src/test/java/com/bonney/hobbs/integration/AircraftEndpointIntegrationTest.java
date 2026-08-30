package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.dto.CreateAircraftDto;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class AircraftEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anAuthenticatedPilotCanRegisterAndListAircraft() {
        HobbsClient pilot = createAuthenticatedClient();

        AircraftDto created = pilot.createAircraft(new CreateAircraftDto("G-ABCD", "Cessna", "152", "SINGLE_ENGINE"));

        assertThat(created.getRegistration(), is("G-ABCD"));
        assertThat(pilot.listAircraft().stream().map(AircraftDto::getId).toList(), contains(created.getId()));
    }

    @Test
    void aircraftIsSharedAcrossPilotsNotScopedToOneAccount() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();

        AircraftDto created = first.createAircraft(new CreateAircraftDto("G-SHRD", "Piper", "PA-28", "SINGLE_ENGINE"));

        assertThat(second.listAircraft().stream().map(AircraftDto::getId).toList(), contains(created.getId()));
    }
}
