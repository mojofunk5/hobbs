package com.bonney.hobbs.domain;

import java.util.UUID;

public final class FlightEntryId extends TypedId {

    private FlightEntryId(UUID value) {
        super(value);
    }

    public static FlightEntryId from(UUID value) {
        return new FlightEntryId(value);
    }

    public static FlightEntryId random() {
        return new FlightEntryId(UUID.randomUUID());
    }
}
