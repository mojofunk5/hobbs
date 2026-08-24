package com.bonney.hobbs.domain;

import java.time.OffsetDateTime;
import java.util.Optional;

public class Sessions {

    public static final int DEFAULT_TTL_HOURS = 24;

    private final SessionRepository repository;
    private final int ttlHours;

    public Sessions(SessionRepository repository) {
        this(repository, DEFAULT_TTL_HOURS);
    }

    public Sessions(SessionRepository repository, int ttlHours) {
        this.repository = repository;
        this.ttlHours = ttlHours;
    }

    public Session create(Pilot pilot) {
        SessionId sessionId = SessionId.random();
        repository.save(sessionId, pilot.getId(), OffsetDateTime.now());
        return new Session(sessionId, pilot);
    }

    // Sliding-window TTL - a valid lookup also bumps the session's last-accessed time, same idle-
    // expiry behavior this class had before moving from an in-memory Guava cache to Postgres.
    public Optional<PilotId> find(SessionId sessionId) {
        return repository.findIfUnexpiredAndTouch(sessionId, ttlHours);
    }

    public void deleteAllForPilot(PilotId pilotId) {
        repository.deleteAllForPilot(pilotId);
    }
}
