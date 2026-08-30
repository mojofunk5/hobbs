package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.ClaimInviteRequestDto;
import com.bonney.hobbs.dto.CreatePilotDto;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import com.bonney.hobbs.dto.LoginDto;
import com.bonney.hobbs.dto.PilotSummaryDto;
import com.bonney.hobbs.dto.RegisterDto;
import com.bonney.hobbs.dto.ReferralCodeDto;
import com.bonney.hobbs.dto.SessionDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PilotEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void searchPilotsAlwaysIncludesTheCallerThemselves() {
        HobbsClient william = createAuthenticatedClient();

        List<PilotSummaryDto> known = william.searchPilots();

        assertThat(known.stream().map(PilotSummaryDto::getName).toList(), contains("testuser"));
    }

    @Test
    void searchPilotsIncludesAPilotTheCallerCreated() {
        HobbsClient william = createAuthenticatedClient();
        william.createPilot(new CreateUnclaimedPilotDto("Louis"));

        List<PilotSummaryDto> known = william.searchPilots();

        assertThat(known.stream().map(PilotSummaryDto::getName).toList(), containsInAnyOrder("testuser", "Louis"));
    }

    @Test
    void searchPilotsIncludesAPilotFlownWithAsPilotInCommandOrCoPilot() {
        HobbsClient william = createAuthenticatedClient();
        UUID aircraftId = seedAircraft("G-ABCD", "Cessna", "152");
        william.createFlightEntry(aFlightEntry(william, aircraftId, null));

        List<PilotSummaryDto> known = william.searchPilots();

        assertThat(known.stream().map(PilotSummaryDto::getName).toList(),
                containsInAnyOrder("testuser", "Instructor Smith"));
    }

    @Test
    void searchPilotsExcludesPilotsUnrelatedToTheCaller() {
        HobbsClient william = createAuthenticatedClient();
        HobbsClient stranger = createAuthenticatedClient();
        stranger.createPilot(new CreateUnclaimedPilotDto("Not Known To William"));

        List<PilotSummaryDto> known = william.searchPilots();

        assertThat(known.stream().map(PilotSummaryDto::getName).toList(), not(hasItem("Not Known To William")));
    }

    @Test
    void searchPilotsFiltersByCaseInsensitiveNameSubstring() {
        HobbsClient william = createAuthenticatedClient();
        william.createPilot(new CreateUnclaimedPilotDto("Louis"));

        assertThat(william.searchPilots("lou").stream().map(PilotSummaryDto::getName).toList(), contains("Louis"));
        assertThat(william.searchPilots("zzz"), is(List.of()));
    }

    @Test
    void pilotCannotUpdateAnotherPilotsProfile() {
        SessionDto alice = register("Alice", "alice-update@example.com", "Password123");
        SessionDto bob = register("Bob", "bob-update@example.com", "Password123");
        HobbsClient aliceClient = createAuthenticatedClient(alice.getSessionId());

        assertThrows(FeignException.Forbidden.class,
                () -> aliceClient.updatePilot(bob.getPilotId(), new CreatePilotDto("Hacked", "hacked@example.com")));
    }

    @Test
    void deletingOwnAccountSucceedsAndPreventsFutureLogin() {
        SessionDto session = register("Del", "del-account@example.com", "Password123");
        HobbsClient authedClient = createAuthenticatedClient(session.getSessionId());

        authedClient.deletePilot(session.getPilotId());

        assertThrows(FeignException.Unauthorized.class,
                () -> createClient().login(new LoginDto("del-account@example.com", "Password123")));
    }

    @Test
    void pilotCannotDeleteAnotherPilotsProfile() {
        SessionDto alice = register("Alice", "alice-delete@example.com", "Password123");
        SessionDto bob = register("Bob", "bob-delete@example.com", "Password123");
        HobbsClient aliceClient = createAuthenticatedClient(alice.getSessionId());

        assertThrows(FeignException.Forbidden.class,
                () -> aliceClient.deletePilot(bob.getPilotId()));
    }

    @Test
    void updatePilotRejectsAMalformedEmailAddress() {
        SessionDto session = register("dana", "dana@example.com", "Password123");
        HobbsClient danaClient = createAuthenticatedClient(session.getSessionId());

        assertThrows(FeignException.BadRequest.class,
                () -> danaClient.updatePilot(session.getPilotId(), new CreatePilotDto("Dana", "not-an-email")));
    }

    @Test
    void invitingAnUnclaimedPilotToClaimLetsThemRegisterAsThatSamePilotId() {
        HobbsClient william = createAuthenticatedClient();
        PilotSummaryDto louis = william.createPilot(new CreateUnclaimedPilotDto("Louis"));

        String code = william.inviteToClaimPilot(louis.getId(), new ClaimInviteRequestDto("louis@example.com")).getCode();
        SessionDto session = createClient().register(new RegisterDto("Louis Actual Name", "louis@example.com", "Password123", code));

        assertThat(session.getPilotId(), is(louis.getId()));
        assertThat(session.getName(), is("Louis Actual Name"));
    }

    @Test
    void onlyTheCreatorOrAnAdminCanInviteAnUnclaimedPilotToClaim() {
        HobbsClient william = createAuthenticatedClient();
        HobbsClient mallory = createAuthenticatedClient();
        PilotSummaryDto louis = william.createPilot(new CreateUnclaimedPilotDto("Louis"));

        assertThrows(FeignException.Forbidden.class,
                () -> mallory.inviteToClaimPilot(louis.getId(), new ClaimInviteRequestDto("louis2@example.com")));
    }

    @Test
    void anAdminCanInviteAnyUnclaimedPilotToClaimEvenWithoutHavingCreatedIt() {
        HobbsClient william = createAuthenticatedClient();
        PilotSummaryDto louis = william.createPilot(new CreateUnclaimedPilotDto("Louis"));

        ReferralCodeDto invite = adminClient.inviteToClaimPilot(louis.getId(), new ClaimInviteRequestDto("louis-admin@example.com"));

        assertThat(invite.getCode(), is(notNullValue()));
    }
}
