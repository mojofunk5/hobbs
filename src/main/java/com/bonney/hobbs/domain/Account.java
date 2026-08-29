package com.bonney.hobbs.domain;

public class Account {

    private final PilotId pilotId;
    private final String email;
    private final boolean disabled;

    public Account(PilotId pilotId, String email, boolean disabled) {
        this.pilotId = pilotId;
        this.email = email;
        this.disabled = disabled;
    }

    public PilotId getPilotId() {
        return pilotId;
    }

    public String getEmail() {
        return email;
    }

    public boolean isDisabled() {
        return disabled;
    }
}
