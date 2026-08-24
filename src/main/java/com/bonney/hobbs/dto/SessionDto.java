package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.util.UUID;

@OpenApiName("Session")
public class SessionDto {

    private final UUID sessionId;
    private final UUID pilotId;
    private final String name;
    private final boolean admin;

    @JsonCreator
    public SessionDto(@JsonProperty("sessionId") UUID sessionId, @JsonProperty("pilotId") UUID pilotId,
                       @JsonProperty("name") String name, @JsonProperty("admin") boolean admin) {
        this.sessionId = sessionId;
        this.pilotId = pilotId;
        this.name = name;
        this.admin = admin;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPilotId() {
        return pilotId;
    }

    public String getName() {
        return name;
    }

    // JSON property "admin", not "isAdmin" - matches Jackson's default derivation from this getter
    // name, rather than fighting it.
    public boolean isAdmin() {
        return admin;
    }
}
