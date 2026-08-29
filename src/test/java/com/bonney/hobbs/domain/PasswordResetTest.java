package com.bonney.hobbs.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetTest {

    @Mock
    Pilots pilots;

    @Mock
    Accounts accounts;

    @Mock
    AuthIdentityRepository authIdentityRepository;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    PasswordResetCodeRepository passwordResetCodeRepository;

    @Mock
    EmailSender emailSender;

    @Mock
    Sessions sessions;

    @Mock
    FailedAttemptRepository failedAttemptRepository;

    PasswordReset passwordReset;

    Pilot alice = new Pilot(PilotId.random(), "Alice", null);
    Account aliceAccount = new Account(alice.getId(), "alice@example.com", false);

    static final int THROTTLE_MAX_ATTEMPTS = 5;
    static final Duration THROTTLE_WINDOW = Duration.ofMinutes(15);

    @BeforeEach
    void setUp() {
        passwordReset = new PasswordReset(pilots, accounts, authIdentityRepository, passwordHasher,
                passwordResetCodeRepository, sessions, emailSender, "http://localhost:5173", 30,
                failedAttemptRepository, THROTTLE_MAX_ATTEMPTS, THROTTLE_WINDOW);
        lenient().when(pilots.get(alice.getId())).thenReturn(Optional.of(alice));
    }

    @Test
    void requestResetForUnknownEmailDoesNothing() {
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        passwordReset.requestReset("nobody@example.com");

        verify(passwordResetCodeRepository, never()).save(any());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void requestResetForKnownEmailInvalidatesOldCodesSavesANewOneAndEmailsIt() {
        when(accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(aliceAccount));

        passwordReset.requestReset("alice@example.com");

        verify(passwordResetCodeRepository).invalidateUnusedForPilot(alice.getId());
        verify(passwordResetCodeRepository).save(any(PasswordResetCode.class));
        verify(emailSender).send(eq("alice@example.com"), anyString(), anyString());
    }

    @Test
    void resetPasswordRejectsAWeakPasswordBeforeAnyLookup() {
        assertThrows(InvalidPasswordException.class,
                () -> passwordReset.resetPassword("alice@example.com", "123456", "weak"));

        verify(accounts, never()).findByEmail(any());
        verify(passwordResetCodeRepository, never()).findUnusedByPilotIdAndCode(any(), any());
    }

    @Test
    void resetPasswordRejectsAnUnknownEmail() {
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetCodeException.class,
                () -> passwordReset.resetPassword("nobody@example.com", "123456", "NewPassword1"));

        verify(authIdentityRepository, never()).updateHashedCredential(any(), any(), any());
        verify(failedAttemptRepository).recordFailure("nobody@example.com", FailedAttemptPurpose.PASSWORD_RESET, THROTTLE_WINDOW);
    }

    @Test
    void resetPasswordRejectsAWrongOrExpiredCode() {
        when(accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(aliceAccount));
        when(passwordResetCodeRepository.findUnusedByPilotIdAndCode(alice.getId(), "999999")).thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetCodeException.class,
                () -> passwordReset.resetPassword("alice@example.com", "999999", "NewPassword1"));

        verify(authIdentityRepository, never()).updateHashedCredential(any(), any(), any());
        verify(passwordResetCodeRepository, never()).markUsed(any());
        verify(failedAttemptRepository).recordFailure("alice@example.com", FailedAttemptPurpose.PASSWORD_RESET, THROTTLE_WINDOW);
    }

    @Test
    void resetPasswordThrowsWhenThrottledWithoutTouchingCredentials() {
        when(failedAttemptRepository.isThrottled("alice@example.com", FailedAttemptPurpose.PASSWORD_RESET,
                THROTTLE_MAX_ATTEMPTS, THROTTLE_WINDOW)).thenReturn(true);

        assertThrows(InvalidPasswordResetCodeException.class,
                () -> passwordReset.resetPassword("alice@example.com", "123456", "NewPassword1"));

        verify(accounts, never()).findByEmail(any());
    }

    @Test
    void resetPasswordDoesNotRecordAFailureOnSuccess() {
        PasswordResetCode code = new PasswordResetCode(PasswordResetCodeId.random(), alice.getId(), "123456",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMinutes(30));
        when(accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(aliceAccount));
        when(passwordResetCodeRepository.findUnusedByPilotIdAndCode(alice.getId(), "123456")).thenReturn(Optional.of(code));
        when(passwordHasher.hash("NewPassword1")).thenReturn("hashed");
        when(sessions.create(alice)).thenReturn(new Session(SessionId.random(), alice));

        passwordReset.resetPassword("alice@example.com", "123456", "NewPassword1");

        verify(failedAttemptRepository, never()).recordFailure(any(), any(), any());
    }

    @Test
    void resetPasswordUpdatesTheHashMarksTheCodeUsedAndReturnsASession() {
        PasswordResetCode code = new PasswordResetCode(PasswordResetCodeId.random(), alice.getId(), "123456",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMinutes(30));
        when(accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(aliceAccount));
        when(passwordResetCodeRepository.findUnusedByPilotIdAndCode(alice.getId(), "123456")).thenReturn(Optional.of(code));
        when(passwordHasher.hash("NewPassword1")).thenReturn("hashed");
        Session expectedSession = new Session(SessionId.random(), alice);
        when(sessions.create(alice)).thenReturn(expectedSession);

        Session session = passwordReset.resetPassword("alice@example.com", "123456", "NewPassword1");

        verify(authIdentityRepository).updateHashedCredential(alice.getId(), AuthIdentityType.PASSWORD, "hashed");
        verify(passwordResetCodeRepository).markUsed(code.getId());
        verify(authIdentityRepository).touchLastLogin(alice.getId(), AuthIdentityType.PASSWORD);
        assertThat(session, is(notNullValue()));
        assertThat(session.getPilot().getName(), is("Alice"));
    }
}
