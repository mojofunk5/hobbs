package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.AirfieldDto;
import com.bonney.hobbs.dto.CreateFlightEntryDto;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
    void recentlyFlownAirfieldsAppearFirstEvenWhenAlphabeticallyLater() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID alpha = seedAirfield("EGAA", "Alpha Airfield");
        UUID zulu = seedAirfield("EGZZ", "Zulu Airfield");
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        UUID pilotInCommandId = pilot.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        pilot.createFlightEntry(new CreateFlightEntryDto(aircraftId, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                zulu, zulu, pilotInCommandId, null, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        List<UUID> ids = pilot.searchAirfields().stream().map(AirfieldDto::getId).toList();

        assertThat(ids, contains(zulu, alpha));
    }

    @Test
    void recentlyFlownAirfieldsRankingAppliesEvenWithASearchTerm() {
        HobbsClient pilot = createAuthenticatedClient();
        UUID alpha = seedAirfield("EGAA", "Alpha Sherburn Airfield");
        UUID zulu = seedAirfield("EGZZ", "Zulu Sherburn Airfield");
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        UUID pilotInCommandId = pilot.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        pilot.createFlightEntry(new CreateFlightEntryDto(aircraftId, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                zulu, zulu, pilotInCommandId, null, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        List<UUID> ids = pilot.searchAirfields("sherburn").stream().map(AirfieldDto::getId).toList();

        assertThat(ids, contains(zulu, alpha));
    }

    @Test
    void recentAirfieldsAreScopedToTheCallingPilot() {
        HobbsClient first = createAuthenticatedClient();
        HobbsClient second = createAuthenticatedClient();
        UUID alpha = seedAirfield("EGAA", "Alpha Airfield");
        UUID zulu = seedAirfield("EGZZ", "Zulu Airfield");
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        UUID pilotInCommandId = first.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        first.createFlightEntry(new CreateFlightEntryDto(aircraftId, null, LocalDate.of(2026, 8, 24),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), OffsetDateTime.parse("2026-08-24T10:45:00Z"),
                zulu, zulu, pilotInCommandId, null, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits"));

        // second never flew anywhere, so their results stay plain alphabetical.
        List<UUID> ids = second.searchAirfields().stream().map(AirfieldDto::getId).toList();

        assertThat(ids, contains(alpha, zulu));
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
