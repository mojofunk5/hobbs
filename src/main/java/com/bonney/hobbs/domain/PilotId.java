package com.bonney.hobbs.domain;

import java.util.UUID;

public final class PilotId extends TypedId {

    private PilotId(UUID value) {
        super(value);
    }

    public static PilotId from(UUID value) {
        return new PilotId(value);
    }

    public static PilotId random() {
        return new PilotId(UUID.randomUUID());
    }
}
