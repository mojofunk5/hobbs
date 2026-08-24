package com.bonney.hobbs.domain;

import java.util.UUID;

public final class SessionId extends TypedId {

    private SessionId(UUID value) {
        super(value);
    }

    public static SessionId from(UUID value) {
        return new SessionId(value);
    }

    public static SessionId random() {
        return new SessionId(UUID.randomUUID());
    }
}
