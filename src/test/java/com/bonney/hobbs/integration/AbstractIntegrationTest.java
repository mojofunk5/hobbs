package com.bonney.hobbs.integration;

import com.bonney.hobbs.AppConfig;
import com.bonney.hobbs.HobbsApplication;
import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.InvitePilotDto;
import com.bonney.hobbs.dto.RegisterDto;
import com.bonney.hobbs.dto.SessionDto;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Clock;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared fixture for every `<Endpoint>EndpointIntegrationTest`: a fresh, isolated
 * {@link HobbsApplication} instance (its own H2 database, its own ephemeral port, its own
 * bootstrapped admin) per test, plus the client-creation/registration helpers every endpoint test
 * needs. See docs/plans/split-integration-test-by-endpoint.md for why this was extracted.
 */
abstract class AbstractIntegrationTest {

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
    // before() with the real Clock.systemUTC(), and by the throttle-window tests with a fixed
    // Clock instead, so their repeated-failure loop can't flake by straddling a real window boundary
    // (see FailedAttemptRepository's own injectable Clock for why - same reasoning as
    // RateLimitRepositoryTest already applies at the unit level).
    record Fixture(HobbsApplication application, OkHttpClient httpClient, HobbsClient adminClient,
                    RecordingEmailSender emailSender) {
    }

    Fixture createFixture(Clock clock) {
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

    HobbsClient createClient() {
        int port = application.getPort();
        return HobbsClient.create("http://localhost:" + port, httpClient);
    }

    HobbsClient createAuthenticatedClient() {
        String email = UUID.randomUUID() + "@test.com";
        String referralCode = adminClient.invitePilot(new InvitePilotDto(email, "testuser")).getCode();
        SessionDto session = createClient().register(new RegisterDto("testuser", email, "Password123", referralCode));
        return createAuthenticatedClient(session.getSessionId());
    }

    HobbsClient createAuthenticatedClient(UUID sessionId) {
        int port = application.getPort();
        return HobbsClient.withAuth("http://localhost:" + port, httpClient, sessionId);
    }

    SessionDto register(String name, String email, String password) {
        String code = adminClient.invitePilot(new InvitePilotDto(email, name)).getCode();
        return createClient().register(new RegisterDto(name, email, password, code));
    }

    String extractResetCode(String email) {
        return extractResetCode(emailSender, email);
    }

    String extractResetCode(RecordingEmailSender fixtureEmailSender, String email) {
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
}
