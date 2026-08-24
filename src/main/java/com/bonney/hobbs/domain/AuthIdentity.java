package com.bonney.hobbs.domain;

public class AuthIdentity {

    private final AuthIdentityId id;
    private final PilotId pilotId;
    private final AuthIdentityType type;
    private final String identifier;
    private final String hashedCredential;

    public AuthIdentity(AuthIdentityId id, PilotId pilotId, AuthIdentityType type, String identifier, String hashedCredential) {
        this.id = id;
        this.pilotId = pilotId;
        this.type = type;
        this.identifier = identifier;
        this.hashedCredential = hashedCredential;
    }

    public AuthIdentityId getId() {
        return id;
    }

    public PilotId getPilotId() {
        return pilotId;
    }

    public AuthIdentityType getType() {
        return type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getHashedCredential() {
        return hashedCredential;
    }
}
