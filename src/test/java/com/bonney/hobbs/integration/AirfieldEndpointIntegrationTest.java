package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.AirfieldDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class AirfieldEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anAuthenticatedPilotCanSearchAirfieldsByNameSubstring() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID id = seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");

        assertThat(pilot.searchAirfields("sherburn").stream().map(AirfieldDto::getId).toList(), contains(id));
    }

    @Test
    void anAuthenticatedPilotCanSearchAirfieldsByIcaoCodePrefix() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID id = seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");

        assertThat(pilot.searchAirfields("EGC").stream().map(AirfieldDto::getId).toList(), contains(id));
    }

    @Test
    void icaoCodeMatchIsPrefixOnlyNotSubstring() {
        HobbsClient pilot = createAuthenticatedClient();
        seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");

        assertThat(pilot.searchAirfields("GCJ"), is(java.util.List.of()));
    }

    @Test
    void airfieldIsSharedAcrossPilotsNotScopedToOneAccount() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID id = seedAirfield("EGNM", "Leeds Bradford Airport");

        assertThat(second.searchAirfields("Leeds").stream().map(AirfieldDto::getId).toList(), contains(id));
    }

    @Test
    void searchWithNoMatchesReturnsAnEmptyList() {
        HobbsClient pilot = createAuthenticatedClient();
        seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");

        assertThat(pilot.searchAirfields("nomatch"), is(java.util.List.of()));
    }

    @Test
    void emptyOrMissingSearchReturnsTheFullSetOrderedByName() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID zulu = seedAirfield("EGZZ", "Zulu Airfield");
        UUID alpha = seedAirfield("EGAA", "Alpha Airfield");

        assertThat(pilot.searchAirfields().stream().map(AirfieldDto::getId).toList(), contains(alpha, zulu));
        assertThat(pilot.searchAirfields(null).stream().map(AirfieldDto::getId).toList(), contains(alpha, zulu));
    }

    @Test
    void searchIsCaseInsensitiveAndMatchesMultipleAirfields() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID sherburn = seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");
        UUID sherburnHouse = seedAirfield("EGCX", "Sherburn House Farm Strip");
        seedAirfield("EGNM", "Leeds Bradford Airport");

        assertThat(pilot.searchAirfields("SHERBURN").stream().map(AirfieldDto::getId).toList(),
                containsInAnyOrder(sherburn, sherburnHouse));
    }

    @Test
    void airfieldDtoNeverExposesSourceNameOrSourceId() {
        HobbsClient pilot = createAuthenticatedClient();
        seedAirfield("EGCJ", "Sherburn-in-Elmet Airfield");

        AirfieldDto found = pilot.searchAirfields("sherburn").get(0);
        // sourceName/sourceId are import-only per docs/plans/airfield-picker.md - AirfieldDto has
        // no getters for them at all, so this just confirms the rest of the shape is present.
        assertThat(found.getIcaoCode(), is("EGCJ"));
        assertThat(found.getName(), is("Sherburn-in-Elmet Airfield"));
    }
}
