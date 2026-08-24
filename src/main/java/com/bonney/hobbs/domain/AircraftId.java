package com.bonney.hobbs.domain;

import java.util.UUID;

public final class AircraftId extends TypedId {

    private AircraftId(UUID value) {
        super(value);
    }

    public static AircraftId from(UUID value) {
        return new AircraftId(value);
    }

    public static AircraftId random() {
        return new AircraftId(UUID.randomUUID());
    }
}
