package com.bonney.hobbs.domain;

import java.util.UUID;

public final class FlightTrackId extends TypedId {

    private FlightTrackId(UUID value) {
        super(value);
    }

    public static FlightTrackId from(UUID value) {
        return new FlightTrackId(value);
    }

    public static FlightTrackId random() {
        return new FlightTrackId(UUID.randomUUID());
    }
}
