package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.time.OffsetDateTime;
import java.util.UUID;

@OpenApiName("Pilot")
public class PilotDto {

    private final UUID id;
    private final String name;
    private final String email;
    private final boolean disabled;
    private final OffsetDateTime signedUpAt;
    private final OffsetDateTime lastLoginAt;

    public PilotDto(@JsonProperty("id") UUID id, @JsonProperty("name") String name,
                      @JsonProperty("email") String email, @JsonProperty("disabled") boolean disabled,
                      @JsonProperty("signedUpAt") OffsetDateTime signedUpAt,
                      @JsonProperty("lastLoginAt") OffsetDateTime lastLoginAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.disabled = disabled;
        this.signedUpAt = signedUpAt;
        this.lastLoginAt = lastLoginAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public OffsetDateTime getSignedUpAt() {
        return signedUpAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }
}
