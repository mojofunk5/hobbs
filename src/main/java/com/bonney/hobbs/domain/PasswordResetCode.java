package com.bonney.hobbs.domain;

import java.time.OffsetDateTime;

public class PasswordResetCode {

    private final PasswordResetCodeId id;
    private final PilotId pilotId;
    private final String code;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime expiresAt;

    public PasswordResetCode(PasswordResetCodeId id, PilotId pilotId, String code, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.id = id;
        this.pilotId = pilotId;
        this.code = code;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public PasswordResetCodeId getId() {
        return id;
    }

    public PilotId getPilotId() {
        return pilotId;
    }

    public String getCode() {
        return code;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
