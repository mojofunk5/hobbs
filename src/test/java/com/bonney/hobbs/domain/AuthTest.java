package com.bonney.hobbs.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTest {

    @Mock
    Pilots pilots;

    @Mock
    AuthIdentityRepository authIdentityRepository;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    AdminBootstrap adminBootstrap;

    @Mock
    AdminRepository adminRepository;

    @Mock
    ReferralCodeRepository referralCodeRepository;

    @Mock
    Sessions sessions;

    @Mock
    FailedAttemptRepository failedAttemptRepository;

    Auth auth;

    Pilot alice = new Pilot(PilotId.random(), "Alice", "alice@example.com");

    static final int LOGIN_THROTTLE_MAX_ATTEMPTS = 10;
    static final Duration LOGIN_THROTTLE_WINDOW = Duration.ofMinutes(15);

    @BeforeEach
    void setUp() {
        auth = new Auth(pilots, authIdentityRepository, sessions, passwordHasher,
                adminBootstrap, adminRepository, referralCodeRepository, failedAttemptRepository,
                LOGIN_THROTTLE_MAX_ATTEMPTS, LOGIN_THROTTLE_WINDOW);
    }

    @Nested
    class Register {

        ReferralCode validCode = new ReferralCode("valid-code", PilotId.random(), java.time.OffsetDateTime.now(), "alice@example.com",
                java.time.OffsetDateTime.now().plusHours(24));

        @Test
        void createsPilotAndAuthIdentityWithReferralCode() {
            when(adminBootstrap.tryConsumeBootstrapCode("valid-code")).thenReturn(false);
            when(referralCodeRepository.findUnusedByCode("valid-code")).thenReturn(Optional.of(validCode));
            when(pilots.create("Alice", "alice@example.com")).thenReturn(alice);
            when(passwordHasher.hash("Password123")).thenReturn("hashed");

            auth.register("Alice", "alice@example.com", "Password123", "valid-code");

            verify(pilots).create("Alice", "alice@example.com");
            verify(authIdentityRepository).save(any(AuthIdentity.class));
            verify(referralCodeRepository).markUsed("valid-code", alice.getId());
            verify(authIdentityRepository).touchLastLogin(alice.getId(), AuthIdentityType.PASSWORD);
        }

        @Test
        void returnsSessionForNewPilot() {
            when(adminBootstrap.tryConsumeBootstrapCode("valid-code")).thenReturn(false);
            when(referralCodeRepository.findUnusedByCode("valid-code")).thenReturn(Optional.of(validCode));
            when(pilots.create("Alice", "alice@example.com")).thenReturn(alice);
            when(passwordHasher.hash("Password123")).thenReturn("hashed");
            Session expectedSession = new Session(SessionId.random(), alice);
            when(sessions.create(alice)).thenReturn(expectedSession);

            Session session = auth.register("Alice", "alice@example.com", "Password123", "valid-code");

            assertThat(session, is(notNullValue()));
            assertThat(session.getPilot().getName(), is("Alice"));
            assertThat(session.getSessionId(), is(notNullValue()));
        }

        @Test
        void throwsForInvalidReferralCode() {
            when(adminBootstrap.tryConsumeBootstrapCode("bad-code")).thenReturn(false);
            when(referralCodeRepository.findUnusedByCode("bad-code")).thenReturn(Optional.empty());

            assertThrows(InvalidReferralCodeException.class,
                    () -> auth.register("Alice", "alice@example.com", "Password123", "bad-code"));

            verify(pilots, never()).create(any(), any());
        }

        @Test
        void throwsWhenSuppliedEmailDoesNotMatchTheCodesInvitedEmail() {
            when(adminBootstrap.tryConsumeBootstrapCode("valid-code")).thenReturn(false);
            when(referralCodeRepository.findUnusedByCode("valid-code")).thenReturn(Optional.of(validCode));

            assertThrows(InvalidReferralCodeException.class,
                    () -> auth.register("Mallory", "mallory@example.com", "Password123", "valid-code"));

            verify(pilots, never()).create(any(), any());
            verify(referralCodeRepository, never()).markUsed(any(), any());
        }

        @Test
        void bootstrapCodeGrantsAdmin() {
            when(adminBootstrap.tryConsumeBootstrapCode("bootstrap")).thenReturn(true);
            when(pilots.create("Alice", "alice@example.com")).thenReturn(alice);
            when(passwordHasher.hash("Password123")).thenReturn("hashed");

            auth.register("Alice", "alice@example.com", "Password123", "bootstrap");

            verify(adminRepository).makeAdmin(alice.getId());
            verify(referralCodeRepository, never()).markUsed(any(), any());
        }

        @Test
        void rejectsAPasswordThatDoesNotMeetThePolicyBeforeAnySideEffects() {
            assertThrows(InvalidPasswordException.class,
                    () -> auth.register("Alice", "alice@example.com", "weak", "valid-code"));

            verify(adminBootstrap, never()).tryConsumeBootstrapCode(any());
            verify(referralCodeRepository, never()).findUnusedByCode(any());
            verify(pilots, never()).create(any(), any());
        }

        @Test
        void aDuplicateEmailAtCreationTimeDoesNotSurfaceAsDuplicateEmailException() {
            // Referral codes are scoped one-per-email (POST /admin/invite already refuses to invite
            // an already-registered email), so this shouldn't be reachable via the normal flow - if
            // it somehow is, it should look like a generic server error, not a clean 409 that would
            // give the public registration endpoint an account-enumeration oracle.
            when(adminBootstrap.tryConsumeBootstrapCode("valid-code")).thenReturn(false);
            when(referralCodeRepository.findUnusedByCode("valid-code")).thenReturn(Optional.of(validCode));
            when(pilots.create("Alice", "alice@example.com")).thenThrow(new DuplicateEmailException("alice@example.com"));

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> auth.register("Alice", "alice@example.com", "Password123", "valid-code"));

            assertThat(thrown, is(not(instanceOf(DuplicateEmailException.class))));
            verify(referralCodeRepository, never()).markUsed(any(), any());
        }
    }

    @Nested
    class Login {

        AuthIdentity aliceIdentity = new AuthIdentity(
                AuthIdentityId.random(), alice.getId(), AuthIdentityType.PASSWORD, "alice@example.com", "hashed");

        @Test
        void returnsSessionForValidCredentials() {
            when(authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, "alice@example.com"))
                    .thenReturn(Optional.of(aliceIdentity));
            when(passwordHasher.verify("password123", "hashed")).thenReturn(true);
            when(pilots.get(alice.getId())).thenReturn(Optional.of(alice));
            when(pilots.isDisabled(alice.getId())).thenReturn(false);
            Session expectedSession = new Session(SessionId.random(), alice);
            when(sessions.create(alice)).thenReturn(expectedSession);

            Session session = auth.login("alice@example.com", "password123");

            assertThat(session.getPilot().getName(), is("Alice"));
            assertThat(session.getSessionId(), is(notNullValue()));
            verify(authIdentityRepository).touchLastLogin(alice.getId(), AuthIdentityType.PASSWORD);
        }

        @Test
        void throwsForUnknownIdentifier() {
            when(authIdentityRepository.findByTypeAndIdentifier(eq(AuthIdentityType.PASSWORD), any()))
                    .thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () -> auth.login("nobody@example.com", "password123"));

            verify(authIdentityRepository, never()).touchLastLogin(any(), any());
        }

        @Test
        void throwsForDeletedPilot() {
            when(authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, "alice@example.com"))
                    .thenReturn(Optional.of(aliceIdentity));
            when(passwordHasher.verify("password123", "hashed")).thenReturn(true);
            when(pilots.get(alice.getId())).thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () -> auth.login("alice@example.com", "password123"));
        }

        @Test
        void throwsForDisabledPilot() {
            when(authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, "alice@example.com"))
                    .thenReturn(Optional.of(aliceIdentity));
            when(passwordHasher.verify("password123", "hashed")).thenReturn(true);
            when(pilots.get(alice.getId())).thenReturn(Optional.of(alice));
            when(pilots.isDisabled(alice.getId())).thenReturn(true);

            assertThrows(InvalidCredentialsException.class, () -> auth.login("alice@example.com", "password123"));
        }

        @Test
        void throwsForWrongPassword() {
            when(authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, "alice@example.com"))
                    .thenReturn(Optional.of(aliceIdentity));
            when(passwordHasher.verify("wrong", "hashed")).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> auth.login("alice@example.com", "wrong"));
        }

        @Test
        void throwsWhenThrottledWithoutTouchingCredentials() {
            when(failedAttemptRepository.isThrottled("alice@example.com", FailedAttemptPurpose.LOGIN,
                    LOGIN_THROTTLE_MAX_ATTEMPTS, LOGIN_THROTTLE_WINDOW)).thenReturn(true);

            assertThrows(InvalidCredentialsException.class, () -> auth.login("alice@example.com", "password123"));

            verify(authIdentityRepository, never()).findByTypeAndIdentifier(any(), any());
        }

        @Test
        void recordsAFailureOnWrongPassword() {
            when(authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, "alice@example.com"))
                    .thenReturn(Optional.of(aliceIdentity));
            when(passwordHasher.verify("wrong", "hashed")).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> auth.login("alice@example.com", "wrong"));

            verify(failedAttemptRepository).recordFailure("alice@example.com", FailedAttemptPurpose.LOGIN, LOGIN_THROTTLE_WINDOW);
        }

        @Test
        void recordsAFailureForAnUnknownIdentifierTheSameAsAWrongPassword() {
            when(authIdentityRepository.findByTypeAndIdentifier(eq(AuthIdentityType.PASSWORD), any()))
                    .thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () -> auth.login("nobody@example.com", "password123"));

            verify(failedAttemptRepository).recordFailure("nobody@example.com", FailedAttemptPurpose.LOGIN, LOGIN_THROTTLE_WINDOW);
        }

        @Test
        void doesNotRecordAFailureOnSuccess() {
            when(authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, "alice@example.com"))
                    .thenReturn(Optional.of(aliceIdentity));
            when(passwordHasher.verify("password123", "hashed")).thenReturn(true);
            when(pilots.get(alice.getId())).thenReturn(Optional.of(alice));
            when(pilots.isDisabled(alice.getId())).thenReturn(false);
            when(sessions.create(alice)).thenReturn(new Session(SessionId.random(), alice));

            auth.login("alice@example.com", "password123");

            verify(failedAttemptRepository, never()).recordFailure(any(), any(), any());
        }

        @Test
        void loginDoesNotEnforceThePasswordPolicySoExistingWeakPasswordsStillWork() {
            // PasswordPolicy only applies at registration - a pilot whose password predates the
            // policy (or was set before it tightened) must still be able to log in with it; they
            // only have to comply the next time they set a new password.
            when(authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, "alice@example.com"))
                    .thenReturn(Optional.of(aliceIdentity));
            when(passwordHasher.verify("weak", "hashed")).thenReturn(true);
            when(pilots.get(alice.getId())).thenReturn(Optional.of(alice));
            when(pilots.isDisabled(alice.getId())).thenReturn(false);
            Session expectedSession = new Session(SessionId.random(), alice);
            when(sessions.create(alice)).thenReturn(expectedSession);

            Session session = auth.login("alice@example.com", "weak");

            assertThat(session.getPilot().getName(), is("Alice"));
        }
    }
}
