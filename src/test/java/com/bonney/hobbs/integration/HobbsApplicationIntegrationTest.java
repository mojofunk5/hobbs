package com.bonney.hobbs.integration;

import com.bonney.hobbs.AppConfig;
import com.bonney.hobbs.HobbsApplication;
import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.domain.EngineCategory;
import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.dto.ClaimInviteRequestDto;
import com.bonney.hobbs.dto.CreateAircraftDto;
import com.bonney.hobbs.dto.CreateFlightEntryDto;
import com.bonney.hobbs.dto.CreatePilotDto;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import com.bonney.hobbs.dto.FlightEntryDto;
import com.bonney.hobbs.dto.InvitePilotDto;
import com.bonney.hobbs.dto.PilotSummaryDto;
import com.bonney.hobbs.dto.LoginDto;
import com.bonney.hobbs.dto.PasswordResetConfirmDto;
import com.bonney.hobbs.dto.PasswordResetRequestDto;
import com.bonney.hobbs.dto.PendingInviteDto;
import com.bonney.hobbs.dto.PilotDto;
import com.bonney.hobbs.dto.PilotPageDto;
import com.bonney.hobbs.dto.ReferralCodeDto;
import com.bonney.hobbs.dto.RegisterDto;
import com.bonney.hobbs.dto.SessionDto;
import com.bonney.hobbs.dto.UpdatePilotAdminDto;
import feign.FeignException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the full stack (real Javalin server, in-memory H2 PostgreSQL-mode database) end to end
 * via HobbsClient. Covers the auth/pilot/admin subsystem (registration, login, referral codes,
 * password reset, admin pilot management) as well as the aircraft/flight-entry endpoints.
 */
class HobbsApplicationIntegrationTest {

    HobbsApplication application;
    OkHttpClient httpClient;
    HobbsClient adminClient;
    RecordingEmailSender emailSender;

    @BeforeEach
    void before() {
        Fixture fx = createFixture(Clock.systemUTC());
        application = fx.application();
        httpClient = fx.httpClient();
        emailSender = fx.emailSender();
        adminClient = fx.adminClient();
    }

    // Groups everything a fresh, isolated application instance needs (its own H2 database, its own
    // ephemeral port, its own bootstrapped admin) behind one Clock parameter - used directly by
    // before() with the real Clock.systemUTC(), and by the throttle-window tests below with a fixed
    // Clock instead, so their repeated-failure loop can't flake by straddling a real window boundary
    // (see FailedAttemptRepository's own injectable Clock for why - same reasoning as
    // RateLimitRepositoryTest already applies at the unit level).
    private record Fixture(HobbsApplication application, OkHttpClient httpClient, HobbsClient adminClient,
                            RecordingEmailSender emailSender) {
    }

    private Fixture createFixture(Clock clock) {
        String dbUrl = "jdbc:h2:mem:test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        AppConfig config = new AppConfig(dbUrl, "sa", "", 10_000, true, 24, null, false,
                null, 587, null, null, null, "http://localhost:5173", 168, 30, 10, 15, 5, 15);
        HobbsApplication.migrate(config);
        RecordingEmailSender fixtureEmailSender = new RecordingEmailSender();
        HobbsApplication fixtureApplication = new HobbsApplication(0, config, fixtureEmailSender, clock);
        OkHttpClient fixtureHttpClient = new OkHttpClient.Builder().build();
        String bootstrapCode = fixtureApplication.getAdminBootstrap().getBootstrapCode();
        HobbsClient bootstrapClient = HobbsClient.create("http://localhost:" + fixtureApplication.getPort(), fixtureHttpClient);
        SessionDto adminSession = bootstrapClient.register(new RegisterDto("admin", "admin@test.com", "Password123", bootstrapCode));
        HobbsClient fixtureAdminClient = HobbsClient.withAuth(
                "http://localhost:" + fixtureApplication.getPort(), fixtureHttpClient, adminSession.getSessionId());
        return new Fixture(fixtureApplication, fixtureHttpClient, fixtureAdminClient, fixtureEmailSender);
    }

    @AfterEach
    void after() {
        application.stop();
    }

    @Test
    void healthReturnsUp() {
        assertThat(createClient().health().getStatus(), is("UP"));
    }

    @Test
    void aFreshlyRegisteredPilotCanLogIn() {
        SessionDto registered = register("William", "william@example.com", "Password123");

        SessionDto loggedIn = createClient().login(new LoginDto("william@example.com", "Password123"));

        assertThat(loggedIn.getPilotId(), is(registered.getPilotId()));
    }

    @Test
    void registeringWithAnUnknownReferralCodeIsForbidden() {
        assertThrows(FeignException.Forbidden.class,
                () -> createClient().register(new RegisterDto("Mallory", "mallory@example.com", "Password123", "not-a-real-code")));
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        assertThrows(FeignException.Unauthorized.class, () -> createClient().listAircraft());
    }

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

    @Test
    void anAdminCanInviteAPilotWhoThenRegistersWithThatCode() {
        String email = "invited@example.com";
        String code = adminClient.invitePilot(new InvitePilotDto(email, "Invited Pilot")).getCode();

        SessionDto session = createClient().register(new RegisterDto("Invited Pilot", email, "Password123", code));

        assertThat(session.getName(), is("Invited Pilot"));
    }

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
        UUID aircraftId = william.createAircraft(new CreateAircraftDto("G-ABCD", "Cessna", "152", "SINGLE_ENGINE")).getId();
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

    private CreateFlightEntryDto aFlightEntry(HobbsClient client, UUID aircraftId, UUID flightTrackId) {
        UUID pilotInCommandId = client.createPilot(new CreateUnclaimedPilotDto("Instructor Smith")).getId();
        LocalDate date = LocalDate.of(2026, 8, 24);
        OffsetDateTime departureTime = OffsetDateTime.parse("2026-08-24T10:00:00Z");
        OffsetDateTime arrivalTime = OffsetDateTime.parse("2026-08-24T10:45:00Z");
        return new CreateFlightEntryDto(aircraftId, flightTrackId, date, "EGCM", departureTime, "EGCM",
                arrivalTime, pilotInCommandId, null, 45, 0, 45, 0, 0, 0, 0, 0, 45, 0, 3, 0, "Circuits");
    }

    private SessionDto register(String name, String email, String password) {
        String code = adminClient.invitePilot(new InvitePilotDto(email, name)).getCode();
        return createClient().register(new RegisterDto(name, email, password, code));
    }

    private SessionDto register(Fixture fx, String name, String email, String password) {
        String code = fx.adminClient().invitePilot(new InvitePilotDto(email, name)).getCode();
        HobbsClient client = HobbsClient.create("http://localhost:" + fx.application().getPort(), fx.httpClient());
        return client.register(new RegisterDto(name, email, password, code));
    }

    private HobbsClient createClient() {
        int port = application.getPort();
        return HobbsClient.create("http://localhost:" + port, httpClient);
    }

    private HobbsClient createAuthenticatedClient() {
        String email = UUID.randomUUID() + "@test.com";
        String referralCode = adminClient.invitePilot(new InvitePilotDto(email, "testuser")).getCode();
        SessionDto session = createClient().register(new RegisterDto("testuser", email, "Password123", referralCode));
        return createAuthenticatedClient(session.getSessionId());
    }

    private HobbsClient createAuthenticatedClient(UUID sessionId) {
        int port = application.getPort();
        return HobbsClient.withAuth("http://localhost:" + port, httpClient, sessionId);
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
    void registerRejectsAnEmailThatIsTooLong() {
        String longEmail = "a".repeat(250) + "@example.com";
        String code = adminClient.invitePilot(new InvitePilotDto(longEmail, null)).getCode();

        assertThrows(FeignException.BadRequest.class,
                () -> createClient().register(new RegisterDto("Alice", longEmail, "Password123", code)));
    }

    @Test
    void registerRejectsAPasswordLongerThan72Characters() {
        String code = adminClient.invitePilot(new InvitePilotDto("longpassword@example.com", null)).getCode();

        assertThrows(FeignException.BadRequest.class,
                () -> createClient().register(new RegisterDto("Alice", "longpassword@example.com", "Ab1" + "a".repeat(70), code)));
    }

    @Test
    void confirmPasswordResetIsThrottledAfterRepeatedFailuresEvenWithTheCorrectCodeAfterwards() {
        // A fixed Clock, not the shared before()/application - the throttle window is 15 minutes, so
        // this can't be a real-time race the way a one-second window would be, but the window is
        // still epoch-aligned rather than relative to the test's own start, so it's not impossible
        // for these calls to straddle a real boundary purely by chance. Fixing "now" removes that
        // possibility entirely rather than just making it rarer - see FailedAttemptRepository's Clock.
        Fixture fx = createFixture(Clock.fixed(Instant.now(), ZoneOffset.UTC));
        try {
            register(fx, "Reset", "reset-throttle@example.com", "OldPassword1");
            createClient(fx).requestPasswordReset(new PasswordResetRequestDto("reset-throttle@example.com"));
            String code = extractResetCode(fx.emailSender(), "reset-throttle@example.com");

            for (int i = 0; i < 5; i++) {
                assertThrows(FeignException.BadRequest.class, () -> createClient(fx).confirmPasswordReset(
                        new PasswordResetConfirmDto("reset-throttle@example.com", "000000", "NewPassword1")));
            }

            // The email is now throttled - even the genuinely correct code is rejected, proving this
            // is throttling and not just repeated wrong-code failures.
            assertThrows(FeignException.BadRequest.class, () -> createClient(fx).confirmPasswordReset(
                    new PasswordResetConfirmDto("reset-throttle@example.com", code, "NewPassword1")));
        } finally {
            fx.application().stop();
        }
    }

    @Test
    void listInvitesOmitsInvitesThatHaveBeenUsed() {
        String code = adminClient.invitePilot(new InvitePilotDto("soon-registered@example.com", null)).getCode();
        createClient().register(new RegisterDto("SoonRegistered", "soon-registered@example.com", "Password123", code));

        List<PendingInviteDto> invites = adminClient.adminListInvites();

        assertThat(invites.stream().anyMatch(i -> i.getEmail().equals("soon-registered@example.com")), is(false));
    }

    @Test
    void requestingAResetForAnUnknownEmailStillSucceedsAndSendsNothing() {
        int sentBefore = emailSender.getSent().size();

        createClient().requestPasswordReset(new PasswordResetRequestDto("nobody-to-reset@example.com"));

        assertThat(emailSender.getSent().size(), is(sentBefore));
    }

    @Test
    void registerRejectsAPasswordThatDoesNotMeetThePolicy() {
        String code = adminClient.invitePilot(new InvitePilotDto("weakpassword@example.com", null)).getCode();

        assertThrows(FeignException.BadRequest.class,
                () -> createClient().register(new RegisterDto("Alice", "weakpassword@example.com", "weak", code)));
    }

    @Test
    void registerReturnConflictForDuplicateEmail() {
        register("Alice", "alice@example.com", "Password123");

        assertThrows(FeignException.Conflict.class,
                () -> register("Alice2", "alice@example.com", "other"));
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
        asPilot.listAircraft(); // session is valid before expiry

        adminClient.adminExpireSessions(session.getPilotId());

        assertThrows(FeignException.Unauthorized.class, asPilot::listAircraft);
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
    void requestingAResetAgainInvalidatesThePreviouslyIssuedCode() {
        register("Reset", "reset-renew@example.com", "OldPassword1");
        createClient().requestPasswordReset(new PasswordResetRequestDto("reset-renew@example.com"));
        String firstCode = extractResetCode("reset-renew@example.com");

        createClient().requestPasswordReset(new PasswordResetRequestDto("reset-renew@example.com"));

        assertThrows(FeignException.BadRequest.class, () -> createClient().confirmPasswordReset(
                new PasswordResetConfirmDto("reset-renew@example.com", firstCode, "NewPassword1")));
    }

    @Test
    void referralCodeIsOneTimeUse() {
        String code = adminClient.invitePilot(new InvitePilotDto("first@example.com", null)).getCode();
        createClient().register(new RegisterDto("First", "first@example.com", "Password123", code));

        assertThrows(FeignException.Forbidden.class,
                () -> createClient().register(new RegisterDto("Second", "second@example.com", "Password123", code)));
    }

    @Test
    void loginIsThrottledAfterRepeatedFailuresEvenWithTheCorrectPasswordAfterwards() {
        // Fixed Clock - see confirmPasswordResetIsThrottledAfterRepeatedFailuresEvenWithTheCorrectCodeAfterwards's
        // comment for why this can't just reuse the shared before()/application fixture.
        Fixture fx = createFixture(Clock.fixed(Instant.now(), ZoneOffset.UTC));
        try {
            register(fx, "Alice", "alice-throttle@example.com", "Password123");

            for (int i = 0; i < 10; i++) {
                assertThrows(FeignException.Unauthorized.class,
                        () -> createClient(fx).login(new LoginDto("alice-throttle@example.com", "wrongpassword")));
            }

            // The identifier is now throttled - even the genuinely correct password is rejected,
            // proving this is throttling and not just repeated wrong-password failures.
            assertThrows(FeignException.Unauthorized.class,
                    () -> createClient(fx).login(new LoginDto("alice-throttle@example.com", "Password123")));
        } finally {
            fx.application().stop();
        }
    }

    @Test
    void regularPilotSessionHasIsAdminFalse() {
        SessionDto session = register("Regular", "regular@example.com", "Password123");
        assertThat(session.isAdmin(), is(false));
    }

    @Test
    void loginThrottleIsPerIdentifierNotGlobal() {
        // Fixed Clock - see confirmPasswordResetIsThrottledAfterRepeatedFailuresEvenWithTheCorrectCodeAfterwards's
        // comment for why this can't just reuse the shared before()/application fixture.
        Fixture fx = createFixture(Clock.fixed(Instant.now(), ZoneOffset.UTC));
        try {
            register(fx, "Alice", "alice-noise@example.com", "Password123");
            register(fx, "Bob", "bob-unaffected@example.com", "Password123");

            for (int i = 0; i < 10; i++) {
                assertThrows(FeignException.Unauthorized.class,
                        () -> createClient(fx).login(new LoginDto("alice-noise@example.com", "wrongpassword")));
            }

            SessionDto session = createClient(fx).login(new LoginDto("bob-unaffected@example.com", "Password123"));
            assertThat(session.getSessionId(), is(notNullValue()));
        } finally {
            fx.application().stop();
        }
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
    void openApiDocumentationDoesNotRequireAuthenticationWhenEnabled() throws Exception {
        Request request = new Request.Builder()
                .url("http://localhost:" + application.getPort() + "/openapi")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code(), is(200));
        }
    }

    @Test
    void versionEndpointDoesNotRequireAuthentication() {
        assertThat(createClient().version().getSha(), is(notNullValue()));
    }

    @Test
    void loginReturnsUnauthorisedForWrongPassword() {
        register("Alice", "alice@example.com", "Password123");

        assertThrows(FeignException.Unauthorized.class,
                () -> createClient().login(new LoginDto("alice@example.com", "wrongpassword")));
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
    void registerCreatesPilotAndReturnsSession() {
        SessionDto session = register("Alice", "alice@example.com", "Password123");

        assertThat(session.getSessionId(), is(notNullValue()));
        assertThat(session.getPilotId(), is(notNullValue()));
        assertThat(session.getName(), is("Alice"));
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
    void loginReturnsSessionForValidCredentials() {
        register("Alice", "alice@example.com", "Password123");

        SessionDto session = createClient().login(new LoginDto("alice@example.com", "Password123"));

        assertThat(session.getSessionId(), is(notNullValue()));
        assertThat(session.getName(), is("Alice"));
    }

    @Test
    void invitingAPilotWithoutANameOmitsTheNameParamFromTheLink() {
        adminClient.invitePilot(new InvitePilotDto("no-name-link@example.com", null));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.htmlBody(), not(containsString("&name=")));
    }

    @Test
    void loginReturnsUnauthorisedForUnknownIdentifier() {
        assertThrows(FeignException.Unauthorized.class,
                () -> createClient().login(new LoginDto("nobody@example.com", "Password123")));
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
    void adminSessionHasIsAdminTrue() {
        SessionDto loggedIn = createClient().login(new LoginDto("admin@test.com", "Password123"));
        assertThat(loggedIn.isAdmin(), is(true));
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
    void registerRejectsAMalformedEmailAddress() {
        String code = adminClient.invitePilot(new InvitePilotDto("not-an-email", null)).getCode();

        assertThrows(FeignException.BadRequest.class,
                () -> createClient().register(new RegisterDto("Alice", "not-an-email", "Password123", code)));
    }

    @Test
    void aPasswordResetCodeCanOnlyBeUsedOnce() {
        register("Reset", "reset-reuse@example.com", "OldPassword1");
        createClient().requestPasswordReset(new PasswordResetRequestDto("reset-reuse@example.com"));
        String code = extractResetCode("reset-reuse@example.com");
        createClient().confirmPasswordReset(new PasswordResetConfirmDto("reset-reuse@example.com", code, "NewPassword1"));

        assertThrows(FeignException.BadRequest.class, () -> createClient().confirmPasswordReset(
                new PasswordResetConfirmDto("reset-reuse@example.com", code, "AnotherPassword2")));
    }

    @Test
    void confirmingWithAWrongCodeIsRejected() {
        register("Reset", "reset-wrongcode@example.com", "OldPassword1");
        createClient().requestPasswordReset(new PasswordResetRequestDto("reset-wrongcode@example.com"));

        assertThrows(FeignException.BadRequest.class, () -> createClient().confirmPasswordReset(
                new PasswordResetConfirmDto("reset-wrongcode@example.com", "000000", "NewPassword1")));
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
    void invitingAPilotWithANameCarriesItThroughToTheSignUpLink() {
        adminClient.invitePilot(new InvitePilotDto("named-link@example.com", "Priya"));

        List<RecordingEmailSender.SentEmail> sent = emailSender.getSent();
        RecordingEmailSender.SentEmail lastSent = sent.get(sent.size() - 1);
        assertThat(lastSent.htmlBody(), containsString("&name=Priya"));
    }

    @Test
    void passwordResetFullRoundTripAllowsLoginWithTheNewPassword() {
        SessionDto registered = register("Reset", "reset-fulltrip@example.com", "OldPassword1");
        createClient().requestPasswordReset(new PasswordResetRequestDto("reset-fulltrip@example.com"));
        String code = extractResetCode("reset-fulltrip@example.com");

        SessionDto confirmed = createClient().confirmPasswordReset(
                new PasswordResetConfirmDto("reset-fulltrip@example.com", code, "NewPassword1"));
        assertThat(confirmed.getSessionId(), is(notNullValue()));

        SessionDto loggedIn = createClient().login(new LoginDto("reset-fulltrip@example.com", "NewPassword1"));
        assertThat(loggedIn.getPilotId(), is(registered.getPilotId()));
    }

    @Test
    void cannotRegisterWithAnEmailDifferentFromTheOneInvited() {
        String code = adminClient.invitePilot(new InvitePilotDto("invited@example.com", null)).getCode();

        assertThrows(FeignException.Forbidden.class,
                () -> createClient().register(new RegisterDto("Mallory", "mallory@example.com", "Password123", code)));
    }

    @Test
    void adminListPilotsWithAnUnrecognizedSortFallsBackToTheDefaultRatherThanErroring() {
        PilotPageDto page = adminClient.adminListPilots(0, 100, "not-a-real-column", "sideways");

        assertThat(page.getPilots(), is(notNullValue()));
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
    void confirmingWithAWeakNewPasswordIsRejected() {
        register("Reset", "reset-weak@example.com", "OldPassword1");
        createClient().requestPasswordReset(new PasswordResetRequestDto("reset-weak@example.com"));
        String code = extractResetCode("reset-weak@example.com");

        assertThrows(FeignException.BadRequest.class, () -> createClient().confirmPasswordReset(
                new PasswordResetConfirmDto("reset-weak@example.com", code, "weak")));
    }

    @Test
    void registerRejectsANameThatIsTooLong() {
        String code = adminClient.invitePilot(new InvitePilotDto("longname@example.com", null)).getCode();

        assertThrows(FeignException.BadRequest.class,
                () -> createClient().register(new RegisterDto("a".repeat(51), "longname@example.com", "Password123", code)));
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
    void registerRequiresValidReferralCode() {
        assertThrows(FeignException.Forbidden.class,
                () -> createClient().register(new RegisterDto("Alice", "alice2@example.com", "Password123", "not-a-real-code")));
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

    @Test
    void deletingAnAccountPreservesTheFlightHistoryUnderTheSamePilotIdAsAnUnclaimedRecord() {
        SessionDto session = register("Del2", "del-flighthistory@example.com", "Password123");
        HobbsClient authedClient = createAuthenticatedClient(session.getSessionId());
        UUID aircraftId = authedClient.createAircraft(new CreateAircraftDto("G-KEEP", "Cessna", "152", "SINGLE_ENGINE")).getId();
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

    private String extractResetCode(String email) {
        return extractResetCode(emailSender, email);
    }

    private String extractResetCode(RecordingEmailSender fixtureEmailSender, String email) {
        RecordingEmailSender.SentEmail lastSent = fixtureEmailSender.getSent().stream()
                .filter(sent -> sent.toAddress().equals(email))
                .reduce((first, second) -> second)
                .orElseThrow();
        Matcher matcher = Pattern.compile("code=(\\d{6})").matcher(lastSent.htmlBody());
        if (!matcher.find()) {
            throw new IllegalStateException("No password reset code found in email to " + email);
        }
        return matcher.group(1);
    }

    private HobbsClient createClient(Fixture fx) {
        return HobbsClient.create("http://localhost:" + fx.application().getPort(), fx.httpClient());
    }
}
