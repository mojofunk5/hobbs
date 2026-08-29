package com.bonney.hobbs.domain;

import com.bonney.hobbs.email.EmailTemplate;
import com.bonney.hobbs.email.PasswordResetEmailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

public class PasswordReset {

    private static final Logger logger = LoggerFactory.getLogger(PasswordReset.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Pilots pilots;
    private final Accounts accounts;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final Sessions sessions;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;
    private final int ttlMinutes;
    private final FailedAttemptRepository failedAttemptRepository;
    private final int throttleMaxAttempts;
    private final Duration throttleWindow;

    public PasswordReset(Pilots pilots, Accounts accounts, AuthIdentityRepository authIdentityRepository, PasswordHasher passwordHasher,
                          PasswordResetCodeRepository passwordResetCodeRepository, Sessions sessions,
                          EmailSender emailSender, String frontendBaseUrl, int ttlMinutes,
                          FailedAttemptRepository failedAttemptRepository, int throttleMaxAttempts,
                          Duration throttleWindow) {
        this.pilots = pilots;
        this.accounts = accounts;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordHasher = passwordHasher;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.sessions = sessions;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.ttlMinutes = ttlMinutes;
        this.failedAttemptRepository = failedAttemptRepository;
        this.throttleMaxAttempts = throttleMaxAttempts;
        this.throttleWindow = throttleWindow;
    }

    // Silently no-ops for an unknown email - same account-enumeration-avoidance principle as the
    // registration duplicate-email fix. Callers can't tell whether an email is registered from this
    // endpoint's response either way.
    public void requestReset(String email) {
        Optional<Account> account = accounts.findByEmail(email);
        if (account.isEmpty()) {
            return;
        }

        PilotId pilotId = account.get().getPilotId();
        Pilot pilot = pilots.get(pilotId).orElseThrow();
        passwordResetCodeRepository.invalidateUnusedForPilot(pilotId);
        String code = generateCode();
        OffsetDateTime now = OffsetDateTime.now();
        passwordResetCodeRepository.save(new PasswordResetCode(
                PasswordResetCodeId.random(), pilotId, code, now, now.plusMinutes(ttlMinutes)));
        logger.info("Password reset requested for pilotId={}", pilotId);

        try {
            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
            String link = frontendBaseUrl + "/reset-password?email=" + encodedEmail + "&code=" + code;
            EmailTemplate template = new PasswordResetEmailTemplate(pilot.getName(), link, code, ttlMinutes);
            emailSender.send(email, template.subject(), template.htmlBody());
        } catch (RuntimeException e) {
            logger.warn("Failed to send password reset email to={}, code is still valid and can be shared manually", email, e);
        }
    }

    public Session resetPassword(String email, String code, String newPassword) {
        PasswordPolicy.validate(newPassword);

        // Keyed by the raw submitted email, not a resolved pilot - same enumeration-avoidance
        // reasoning as the login throttle. Checked before any code lookup.
        if (failedAttemptRepository.isThrottled(email, FailedAttemptPurpose.PASSWORD_RESET, throttleMaxAttempts, throttleWindow)) {
            throw new InvalidPasswordResetCodeException();
        }

        Account account = accounts.findByEmail(email).orElse(null);
        Pilot pilot = account == null ? null : pilots.get(account.getPilotId()).orElse(null);
        PasswordResetCode resetCode = pilot == null ? null
                : passwordResetCodeRepository.findUnusedByPilotIdAndCode(pilot.getId(), code).orElse(null);
        if (pilot == null || resetCode == null) {
            failedAttemptRepository.recordFailure(email, FailedAttemptPurpose.PASSWORD_RESET, throttleWindow);
            throw new InvalidPasswordResetCodeException();
        }

        String hash = passwordHasher.hash(newPassword);
        authIdentityRepository.updateHashedCredential(pilot.getId(), AuthIdentityType.PASSWORD, hash);
        passwordResetCodeRepository.markUsed(resetCode.getId());
        authIdentityRepository.touchLastLogin(pilot.getId(), AuthIdentityType.PASSWORD);
        logger.info("Password reset completed for pilotId={}", pilot.getId());

        return sessions.create(pilot);
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
