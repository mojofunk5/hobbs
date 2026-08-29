package com.bonney.hobbs.domain;

import java.time.OffsetDateTime;

public class ReferralCode {

    private final String code;
    private final PilotId createdBy;
    private final OffsetDateTime createdAt;
    private final String invitedEmail;
    private final OffsetDateTime expiresAt;
    private final PilotId claimsPilotId;

    public ReferralCode(String code, PilotId createdBy, OffsetDateTime createdAt, String invitedEmail, OffsetDateTime expiresAt) {
        this(code, createdBy, createdAt, invitedEmail, expiresAt, null);
    }

    // claimsPilotId set means registering with this code attaches to that existing, still-unclaimed
    // Pilot rather than creating a new one - see docs/plans/pilot-account-split.md.
    public ReferralCode(String code, PilotId createdBy, OffsetDateTime createdAt, String invitedEmail, OffsetDateTime expiresAt, PilotId claimsPilotId) {
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.invitedEmail = invitedEmail;
        this.expiresAt = expiresAt;
        this.claimsPilotId = claimsPilotId;
    }

    public String getCode() {
        return code;
    }

    public PilotId getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getInvitedEmail() {
        return invitedEmail;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public PilotId getClaimsPilotId() {
        return claimsPilotId;
    }
}
