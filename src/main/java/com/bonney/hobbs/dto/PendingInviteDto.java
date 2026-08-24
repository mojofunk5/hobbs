package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.time.OffsetDateTime;

@OpenApiName("PendingInvite")
public class PendingInviteDto {

    private final String email;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime expiresAt;
    private final boolean expired;

    public PendingInviteDto(@JsonProperty("email") String email, @JsonProperty("createdAt") OffsetDateTime createdAt,
                             @JsonProperty("expiresAt") OffsetDateTime expiresAt, @JsonProperty("expired") boolean expired) {
        this.email = email;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.expired = expired;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expired;
    }
}
