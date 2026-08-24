package com.bonney.hobbs.domain;

public class Session {

    private final SessionId sessionId;
    private final Pilot pilot;

    public Session(SessionId sessionId, Pilot pilot) {
        this.sessionId = sessionId;
        this.pilot = pilot;
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    public Pilot getPilot() {
        return pilot;
    }
}
