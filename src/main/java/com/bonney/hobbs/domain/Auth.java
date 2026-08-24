package com.bonney.hobbs.domain;

import java.time.Duration;

public class Auth {

    private final Pilots pilots;
    private final AuthIdentityRepository authIdentityRepository;
    private final Sessions sessions;
    private final PasswordHasher passwordHasher;
    private final AdminBootstrap adminBootstrap;
    private final AdminRepository adminRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final FailedAttemptRepository failedAttemptRepository;
    private final int loginThrottleMaxAttempts;
    private final Duration loginThrottleWindow;

    public Auth(Pilots pilots, AuthIdentityRepository authIdentityRepository, Sessions sessions,
                PasswordHasher passwordHasher, AdminBootstrap adminBootstrap,
                AdminRepository adminRepository, ReferralCodeRepository referralCodeRepository,
                FailedAttemptRepository failedAttemptRepository, int loginThrottleMaxAttempts,
                Duration loginThrottleWindow) {
        this.pilots = pilots;
        this.authIdentityRepository = authIdentityRepository;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.adminBootstrap = adminBootstrap;
        this.adminRepository = adminRepository;
        this.referralCodeRepository = referralCodeRepository;
        this.failedAttemptRepository = failedAttemptRepository;
        this.loginThrottleMaxAttempts = loginThrottleMaxAttempts;
        this.loginThrottleWindow = loginThrottleWindow;
    }

    public Session register(String name, String email, String password, String referralCode) {
        PasswordPolicy.validate(password);

        boolean isBootstrap = adminBootstrap.tryConsumeBootstrapCode(referralCode);
        if (!isBootstrap) {
            ReferralCode code = referralCodeRepository.findUnusedByCode(referralCode)
                    .orElseThrow(InvalidReferralCodeException::new);
            if (!code.getInvitedEmail().equals(email)) {
                throw new InvalidReferralCodeException();
            }
        }

        Pilot pilot;
        try {
            pilot = pilots.create(name, email);
        } catch (DuplicateEmailException e) {
            // A referral code is scoped to one unused code per email (POST /admin/invite already
            // refuses to invite an email that's already a registered pilot), so a real duplicate
            // here means that guarantee was somehow bypassed - a genuine server-side anomaly, not a
            // case worth giving the caller a clean, distinguishable 409 for (that would just be an
            // account-enumeration oracle on the public registration endpoint).
            throw new IllegalStateException("Unexpected duplicate email during registration: " + email, e);
        }
        String hash = passwordHasher.hash(password);
        AuthIdentity identity = new AuthIdentity(AuthIdentityId.random(), pilot.getId(), AuthIdentityType.PASSWORD, email, hash);
        authIdentityRepository.save(identity);

        if (isBootstrap) {
            adminRepository.makeAdmin(pilot.getId());
        } else {
            referralCodeRepository.markUsed(referralCode, pilot.getId());
        }

        authIdentityRepository.touchLastLogin(pilot.getId(), AuthIdentityType.PASSWORD);
        return sessions.create(pilot);
    }

    public Session login(String identifier, String password) {
        // Keyed by the raw submitted identifier, not a resolved pilot - an unknown identifier is
        // throttled identically to a real one, so this can't become a fresh account-enumeration
        // oracle. Checked before any password verification so an already-throttled caller doesn't
        // pay for a wasted bcrypt check.
        if (failedAttemptRepository.isThrottled(identifier, FailedAttemptPurpose.LOGIN, loginThrottleMaxAttempts, loginThrottleWindow)) {
            throw new InvalidCredentialsException();
        }

        AuthIdentity identity = authIdentityRepository.findByTypeAndIdentifier(AuthIdentityType.PASSWORD, identifier)
                .orElse(null);
        if (identity == null || !passwordHasher.verify(password, identity.getHashedCredential())) {
            failedAttemptRepository.recordFailure(identifier, FailedAttemptPurpose.LOGIN, loginThrottleWindow);
            throw new InvalidCredentialsException();
        }
        Pilot pilot = pilots.get(identity.getPilotId()).orElse(null);
        if (pilot == null || pilots.isDisabled(pilot.getId())) {
            failedAttemptRepository.recordFailure(identifier, FailedAttemptPurpose.LOGIN, loginThrottleWindow);
            throw new InvalidCredentialsException();
        }
        authIdentityRepository.touchLastLogin(pilot.getId(), AuthIdentityType.PASSWORD);
        return sessions.create(pilot);
    }
}
