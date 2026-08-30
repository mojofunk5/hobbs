package com.bonney.hobbs.integration;

import com.bonney.hobbs.client.HobbsClient;
import com.bonney.hobbs.dto.InvitePilotDto;
import com.bonney.hobbs.dto.LoginDto;
import com.bonney.hobbs.dto.PasswordResetConfirmDto;
import com.bonney.hobbs.dto.PasswordResetRequestDto;
import com.bonney.hobbs.dto.RegisterDto;
import com.bonney.hobbs.dto.SessionDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthEndpointIntegrationTest extends AbstractIntegrationTest {

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
        assertThrows(FeignException.Unauthorized.class, () -> createClient().searchAircraft("xx"));
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
    void loginReturnsUnauthorisedForWrongPassword() {
        register("Alice", "alice@example.com", "Password123");

        assertThrows(FeignException.Unauthorized.class,
                () -> createClient().login(new LoginDto("alice@example.com", "wrongpassword")));
    }

    @Test
    void registerCreatesPilotAndReturnsSession() {
        SessionDto session = register("Alice", "alice@example.com", "Password123");

        assertThat(session.getSessionId(), is(notNullValue()));
        assertThat(session.getPilotId(), is(notNullValue()));
        assertThat(session.getName(), is("Alice"));
    }

    @Test
    void loginReturnsSessionForValidCredentials() {
        register("Alice", "alice@example.com", "Password123");

        SessionDto session = createClient().login(new LoginDto("alice@example.com", "Password123"));

        assertThat(session.getSessionId(), is(notNullValue()));
        assertThat(session.getName(), is("Alice"));
    }

    @Test
    void loginReturnsUnauthorisedForUnknownIdentifier() {
        assertThrows(FeignException.Unauthorized.class,
                () -> createClient().login(new LoginDto("nobody@example.com", "Password123")));
    }

    @Test
    void adminSessionHasIsAdminTrue() {
        SessionDto loggedIn = createClient().login(new LoginDto("admin@test.com", "Password123"));
        assertThat(loggedIn.isAdmin(), is(true));
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
    void registerRequiresValidReferralCode() {
        assertThrows(FeignException.Forbidden.class,
                () -> createClient().register(new RegisterDto("Alice", "alice2@example.com", "Password123", "not-a-real-code")));
    }

    private SessionDto register(Fixture fx, String name, String email, String password) {
        String code = fx.adminClient().invitePilot(new InvitePilotDto(email, name)).getCode();
        HobbsClient client = HobbsClient.create("http://localhost:" + fx.application().getPort(), fx.httpClient());
        return client.register(new RegisterDto(name, email, password, code));
    }

    private HobbsClient createClient(Fixture fx) {
        return HobbsClient.create("http://localhost:" + fx.application().getPort(), fx.httpClient());
    }
}
