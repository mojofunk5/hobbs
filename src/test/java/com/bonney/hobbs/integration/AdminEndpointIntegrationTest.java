package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import com.bonney.hobbs.dto.FlightEntryDto;
import com.bonney.hobbs.dto.InvitePilotDto;
import com.bonney.hobbs.dto.PilotSummaryDto;
import com.bonney.hobbs.dto.LoginDto;
import com.bonney.hobbs.dto.PasswordResetConfirmDto;
import com.bonney.hobbs.dto.PendingInviteDto;
import com.bonney.hobbs.dto.PilotDto;
import com.bonney.hobbs.dto.PilotPageDto;
import com.bonney.hobbs.dto.ReferralCodeDto;
import com.bonney.hobbs.dto.RegisterDto;
import com.bonney.hobbs.dto.SessionDto;
import com.bonney.hobbs.dto.UpdatePilotAdminDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the full stack (real Javalin server, in-memory H2 PostgreSQL-mode database) end to end
 * via HobbsClient. Covers the admin subsystem: pilot list/invite/disable/delete/expire-sessions/
 * cancel-invite. The last of the endpoint splits - see docs/plans/split-integration-test-by-endpoint.md
 * for the full history; Health/Aircraft/FlightEntry/Pilot/Auth coverage now live in their own
 * <Endpoint>EndpointIntegrationTest classes, and this class replaces the original
 * HobbsApplicationIntegrationTest that once held all of them.
 */
class AdminEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anAdminCanInviteAPilotWhoThenRegistersWithThatCode() {
        String email = "invited@example.com";
        String code = adminClient.invitePilot(new InvitePilotDto(email, "Invited Pilot")).getCode();

        SessionDto session = createClient().register(new RegisterDto("Invited Pilot", email, "Password123", code));

        assertThat(session.getName(), is("Invited Pilot"));
    }

    @Test
    void adminCanDeletePilot() {
        SessionDto session = register("ToDelete", "todelete@example.com", "Password123");
        adminClient.adminDeletePilot(session.getPilotId());

        assertThrows(FeignException.Unauthorized.class,
                () -> createClient().login(new LoginDto("todelete@example.com", "Password123")));
    }

    @Test
    void adminListPilotsDefaultsToPageSizeTenAndReportsTheRealTotal() {
        // before() already registers the admin - read the real baseline rather than hardcoding an
        // assumption about how many pilots exist.
        int baseline = adminClient.adminListPilots().getTotal();
        for (int i = 0; i < 12; i++) {
            register("Page" + i, "page" + i + "@example.com", "Password123");
        }

        PilotPageDto page = adminClient.adminListPilots();

        assertThat(page.getPageSize(), is(10));
        assertThat(page.getPage(), is(0));
        assertThat(page.getPilots().size(), is(10));
        assertThat(page.getTotal(), is(baseline + 12));
    }

    @Test
    void listInvitesOmitsInvitesThatHaveBeenUsed() {
        String code = adminClient.invitePilot(new InvitePilotDto("soon-registered@example.com", null)).getCode();
        createClient().register(new RegisterDto("SoonRegistered", "soon-registered@example.com", "Password123", code));

        List<PendingInviteDto> invites = adminClient.adminListInvites();

        assertThat(invites.stream().anyMatch(i -> i.getEmail().equals("soon-registered@example.com")), is(false));
    }

    @Test
    void listInvitesOmitsAnEmailThatRegisteredWithAResentCode() {
        adminClient.invitePilot(new InvitePilotDto("resent-then-registered@example.com", null));
        String freshCode = adminClient.invitePilot(new InvitePilotDto("resent-then-registered@example.com", null)).getCode();

        createClient().register(new RegisterDto("ResentThenRegistered", "resent-then-registered@example.com", "Password123", freshCode));

        List<PendingInviteDto> invites = adminClient.adminListInvites();

        assertThat(invites.stream().anyMatch(i -> i.getEmail().equals("resent-then-registered@example.com")), is(false));
    }

    @Test
    void cancellingAnInviteRemovesItFromTheListAndInvalidatesTheCode() {
        String code = adminClient.invitePilot(new InvitePilotDto("cancelled@example.com", null)).getCode();

        adminClient.cancelInvite("cancelled@example.com");

        assertThat(adminClient.adminListInvites().stream().anyMatch(i -> i.getEmail().equals("cancelled@example.com")), is(false));
        assertThrows(FeignException.Forbidden.class,
                () -> createClient().register(new RegisterDto("Cancelled", "cancelled@example.com", "Password123", code)));
    }

    @Test
    void adminCanListPilotsAndSeesDisabledStatus() {
        SessionDto session = register("Listed", "listed@example.com", "Password123");
        adminClient.adminUpdatePilot(session.getPilotId(), new UpdatePilotAdminDto(false));

        List<PilotDto> pilots = adminClient.adminListPilots().getPilots();

        PilotDto listed = pilots.stream()
                .filter(p -> p.getId().equals(session.getPilotId()))
                .findFirst().orElseThrow();
        assertThat(listed.getEmail(), is("listed@example.com"));
        assertThat(listed.isDisabled(), is(true));
    }

    @Test
    void adminCanExpirePilotSession() {
        SessionDto session = register("ToExpire", "toexpire@example.com", "Password123");
        HobbsClient asPilot = createAuthenticatedClient(session.getSessionId());
        asPilot.searchAircraft("xx"); // session is valid before expiry

        adminClient.adminExpireSessions(session.getPilotId());

        assertThrows(FeignException.Unauthorized.class, () -> asPilot.searchAircraft("xx"));
    }

    @Test
    void invitingAPilotSendsThemAnEmailWithTheCode() {
        ReferralCodeDto invite = adminClient.invitePilot(new InvitePilotDto("invitee@example.com", null));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.toAddress(), is("invitee@example.com"));
        assertThat(lastSent.htmlBody(), containsString(invite.getCode()));
        assertThat(lastSent.htmlBody(), containsString("http://localhost:5173/create-pilot?code=" + invite.getCode() + "&email=invitee%40example.com"));
        assertThat(lastSent.htmlBody(), containsString("Love,<br>Andy and the team @bssd.co.uk"));
        assertThat(lastSent.htmlBody(), containsString("This invite expires in 7 days"));
    }

    @Test
    void adminSendPasswordResetSendsARealEmailToThePilot() {
        SessionDto session = register("Reset", "admin-reset-target@example.com", "Password123");
        int sentBefore = emailSender.getSent().size();

        adminClient.adminSendPasswordReset(session.getPilotId());

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        assertThat(sent.size(), is(sentBefore + 1));
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.toAddress(), is("admin-reset-target@example.com"));

        // The code this sends is a genuinely usable one - confirm the full round trip works, not
        // just that an email was sent.
        String code = extractResetCode("admin-reset-target@example.com");
        SessionDto confirmed = createClient().confirmPasswordReset(
                new PasswordResetConfirmDto("admin-reset-target@example.com", code, "BrandNewPassword1"));
        assertThat(confirmed.getSessionId(), is(notNullValue()));
    }

    @Test
    void listInvitesShowsOnlyTheNewestInviteAfterAReInvite() {
        adminClient.invitePilot(new InvitePilotDto("lapsed@example.com", null));
        adminClient.invitePilot(new InvitePilotDto("lapsed@example.com", null));

        List<PendingInviteDto> invites = adminClient.adminListInvites().stream()
                .filter(i -> i.getEmail().equals("lapsed@example.com")).toList();

        assertThat(invites, hasSize(1));
        assertThat(invites.get(0).isExpired(), is(false));
    }

    @Test
    void cannotInviteAnAlreadyRegisteredEmail() {
        register("Existing", "existing-pilot@example.com", "Password123");
        int sentBefore = emailSender.getSent().size();

        assertThrows(FeignException.Conflict.class,
                () -> adminClient.invitePilot(new InvitePilotDto("existing-pilot@example.com", null)));

        assertThat(emailSender.getSent().size(), is(sentBefore));
    }

    @Test
    void adminCanDisablePilotAndPreventLogin() {
        SessionDto session = register("Victim", "victim@example.com", "Password123");
        adminClient.adminUpdatePilot(session.getPilotId(), new UpdatePilotAdminDto(false));

        assertThrows(FeignException.Unauthorized.class,
                () -> createClient().login(new LoginDto("victim@example.com", "Password123")));
    }

    @Test
    void adminCanListPilotsAndSeesSignUpDate() {
        OffsetDateTime beforeRegistration = OffsetDateTime.now().minusSeconds(1);
        SessionDto session = register("SignedUp", "signedup@example.com", "Password123");

        List<PilotDto> pilots = adminClient.adminListPilots().getPilots();

        PilotDto listed = pilots.stream()
                .filter(p -> p.getId().equals(session.getPilotId()))
                .findFirst().orElseThrow();
        assertThat(listed.getSignedUpAt(), is(notNullValue()));
        assertThat(listed.getSignedUpAt().isAfter(beforeRegistration), is(true));
    }

    @Test
    void invitingAPilotWithANameGreetsThemByName() {
        adminClient.invitePilot(new InvitePilotDto("named-invitee@example.com", "Priya"));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.htmlBody(), containsString("Hi Priya,"));
    }

    @Test
    void invitingAPilotWithoutANameUsesAGenericGreeting() {
        adminClient.invitePilot(new InvitePilotDto("anonymous-invitee@example.com", null));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.htmlBody(), containsString("Hi,"));
        assertThat(lastSent.htmlBody(), not(containsString("Hi null")));
    }

    @Test
    void adminListPilotsSortsByNameDescending() {
        register("Alice", "sort-alice@example.com", "Password123");
        register("Zeb", "sort-zeb@example.com", "Password123");

        PilotPageDto page = adminClient.adminListPilots(0, 100, "name", "desc");

        List<String> names = page.getPilots().stream().map(PilotDto::getName).toList();
        assertThat(names.indexOf("Zeb"), lessThan(names.indexOf("Alice")));
    }

    @Test
    void invitingAPilotWithASpecialCharacterEmailUrlEncodesItInTheLink() {
        adminClient.invitePilot(new InvitePilotDto("first+last@example.com", null));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.htmlBody(), containsString("&email=first%2Blast%40example.com"));
    }

    @Test
    void adminListPilotsPageSizeOver100IsRejected() {
        assertThrows(FeignException.BadRequest.class, () -> adminClient.adminListPilots(0, 101, "name", "asc"));
    }

    @Test
    void adminCanReEnableADisabledPilot() {
        SessionDto session = register("Reinstated", "reinstated@example.com", "Password123");
        adminClient.adminUpdatePilot(session.getPilotId(), new UpdatePilotAdminDto(false));
        adminClient.adminUpdatePilot(session.getPilotId(), new UpdatePilotAdminDto(true));

        SessionDto loggedIn = createClient().login(new LoginDto("reinstated@example.com", "Password123"));
        assertThat(loggedIn.getSessionId(), is(notNullValue()));
    }

    @Test
    void invitingAPilotWithoutANameOmitsTheNameParamFromTheLink() {
        adminClient.invitePilot(new InvitePilotDto("no-name-link@example.com", null));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.htmlBody(), not(containsString("&name=")));
    }

    @Test
    void adminSendPasswordResetForAnUnknownPilotIdIsNotFound() {
        assertThrows(FeignException.NotFound.class, () -> adminClient.adminSendPasswordReset(UUID.randomUUID()));
    }

    @Test
    void listInvitesShowsAPendingInvite() {
        adminClient.invitePilot(new InvitePilotDto("pending@example.com", null));

        List<PendingInviteDto> invites = adminClient.adminListInvites();

        PendingInviteDto invite = invites.stream().filter(i -> i.getEmail().equals("pending@example.com")).findFirst().orElseThrow();
        assertThat(invite.isExpired(), is(false));
    }

    @Test
    void unauthenticatedRequestToAnAdminEndpointIsRejected() {
        assertThrows(FeignException.Unauthorized.class, () -> createClient().adminListPilots());
    }

    @Test
    void adminListPilotsRespectsPageAndPageSize() {
        int baseline = adminClient.adminListPilots().getTotal();
        register("First", "first@example.com", "Password123");
        register("Second", "second@example.com", "Password123");
        register("Third", "third@example.com", "Password123");

        PilotPageDto firstPage = adminClient.adminListPilots(0, 2, "name", "asc");

        assertThat(firstPage.getPilots().size(), is(2));
        assertThat(firstPage.getTotal(), is(baseline + 3));
    }

    @Test
    void invitingAPilotWithANameCarriesItThroughToTheSignUpLink() {
        adminClient.invitePilot(new InvitePilotDto("named-link@example.com", "Priya"));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.htmlBody(), containsString("&name=Priya"));
    }

    @Test
    void adminListPilotsWithAnUnrecognizedSortFallsBackToTheDefaultRatherThanErroring() {
        PilotPageDto page = adminClient.adminListPilots(0, 100, "not-a-real-column", "sideways");

        assertThat(page.getPilots(), is(notNullValue()));
    }

    @Test
    void reInvitingAfterCancellingWorksNormally() {
        adminClient.invitePilot(new InvitePilotDto("revived@example.com", null));
        adminClient.cancelInvite("revived@example.com");

        String freshCode = adminClient.invitePilot(new InvitePilotDto("revived@example.com", null)).getCode();

        createClient().register(new RegisterDto("Revived", "revived@example.com", "Password123", freshCode));
    }

    @Test
    void nonAdminCannotAccessAdminEndpoints() {
        HobbsClient regularClient = createAuthenticatedClient();

        assertThrows(FeignException.Forbidden.class,
                () -> regularClient.invitePilot(new InvitePilotDto("nobody@example.com", null)));
        assertThrows(FeignException.Forbidden.class, regularClient::adminListInvites);
        assertThrows(FeignException.Forbidden.class, regularClient::adminListPilots);
        assertThrows(FeignException.Forbidden.class,
                () -> regularClient.adminUpdatePilot(UUID.randomUUID(), new UpdatePilotAdminDto(false)));
        assertThrows(FeignException.Forbidden.class, () -> regularClient.adminDeletePilot(UUID.randomUUID()));
        assertThrows(FeignException.Forbidden.class, () -> regularClient.adminExpireSessions(UUID.randomUUID()));
        assertThrows(FeignException.Forbidden.class, () -> regularClient.cancelInvite("nobody@example.com"));
    }

    @Test
    void reInvitingAnEmailExpiresThePreviousCode() {
        String firstCode = adminClient.invitePilot(new InvitePilotDto("renewed@example.com", null)).getCode();
        String secondCode = adminClient.invitePilot(new InvitePilotDto("renewed@example.com", null)).getCode();

        assertThrows(FeignException.Forbidden.class,
                () -> createClient().register(new RegisterDto("Renewed", "renewed@example.com", "Password123", firstCode)));
        createClient().register(new RegisterDto("Renewed", "renewed@example.com", "Password123", secondCode));
    }

    @Test
    void adminCanListPilotsAndSeesLastLoginDateUpdatingOnEachLogin() {
        OffsetDateTime beforeRegistration = OffsetDateTime.now().minusSeconds(1);
        SessionDto session = register("LastLogin", "lastlogin@example.com", "Password123");

        PilotDto afterRegistration = adminClient.adminListPilots().getPilots().stream()
                .filter(p -> p.getId().equals(session.getPilotId()))
                .findFirst().orElseThrow();
        assertThat(afterRegistration.getLastLoginAt(), is(notNullValue()));
        assertThat(afterRegistration.getLastLoginAt().isAfter(beforeRegistration), is(true));

        OffsetDateTime beforeLogin = OffsetDateTime.now().minusSeconds(1);
        createClient().login(new LoginDto("lastlogin@example.com", "Password123"));

        PilotDto afterLogin = adminClient.adminListPilots().getPilots().stream()
                .filter(p -> p.getId().equals(session.getPilotId()))
                .findFirst().orElseThrow();
        assertThat(afterLogin.getLastLoginAt().isAfter(beforeLogin), is(true));
    }

    @Test
    void anUnclaimedPilotAppearsInTheAdminListWithNullEmailAndDisabled() {
        HobbsClient william = createAuthenticatedClient();

        PilotSummaryDto louis = william.createPilot(new CreateUnclaimedPilotDto("Louis"));

        PilotDto listed = adminClient.adminListPilots(0, 100, "name", "asc").getPilots().stream()
                .filter(p -> p.getId().equals(louis.getId()))
                .findFirst().orElseThrow();
        assertThat(listed.getName(), is("Louis"));
        assertThat(listed.getEmail(), is((String) null));
        assertThat(listed.isDisabled(), is((Boolean) null));
    }

    @Test
    void deletingAnAccountPreservesTheFlightHistoryUnderTheSamePilotIdAsAnUnclaimedRecord() {
        SessionDto session = register("Del2", "del-flighthistory@example.com", "Password123");
        HobbsClient authedClient = createAuthenticatedClient(session.getSessionId());
        UUID aircraftId = seedAircraft("G-KEEP", "Cessna", "152");
        FlightEntryDto flight = authedClient.createFlightEntry(aFlightEntry(authedClient, aircraftId, null));

        authedClient.deletePilot(session.getPilotId());

        // The pre-existing session isn't invalidated by account deletion (SessionAuthFilter only
        // checks session validity, not account status, on each request - same documented gap as
        // disable), so it's still usable here purely to prove the flight entry itself survived.
        FlightEntryDto stillThere = authedClient.getFlightEntry(flight.getId());
        assertThat(stillThere.getId(), is(flight.getId()));
        PilotDto listed = adminClient.adminListPilots(0, 100, "name", "asc").getPilots().stream()
                .filter(p -> p.getId().equals(session.getPilotId()))
                .findFirst().orElseThrow();
        assertThat(listed.getEmail(), is((String) null));
        assertThat(listed.isDisabled(), is((Boolean) null));
    }

    @Test
    void anAdminCanReInviteAPilotWhoseAccountWasDeleted() {
        SessionDto session = register("Revivable", "revivable@example.com", "Password123");
        adminClient.adminDeletePilot(session.getPilotId());

        String freshCode = adminClient.invitePilot(new InvitePilotDto("revivable-new@example.com", null)).getCode();
        SessionDto reRegistered = createClient().register(new RegisterDto("Revivable", "revivable-new@example.com", "Password123", freshCode));

        assertThat(reRegistered.getPilotId(), is(not(session.getPilotId())));
    }
}
