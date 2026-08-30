package com.bonney.hobbs.domain;

import java.util.UUID;

public final class AirfieldId extends TypedId {

    private AirfieldId(UUID value) {
        super(value);
    }

    public static AirfieldId from(UUID value) {
        return new AirfieldId(value);
    }

    public static AirfieldId random() {
        return new AirfieldId(UUID.randomUUID());
    }
}
