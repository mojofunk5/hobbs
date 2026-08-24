package com.bonney.hobbs.domain;

import java.util.UUID;

public final class SimulatorSessionId extends TypedId {

    private SimulatorSessionId(UUID value) {
        super(value);
    }

    public static SimulatorSessionId from(UUID value) {
        return new SimulatorSessionId(value);
    }

    public static SimulatorSessionId random() {
        return new SimulatorSessionId(UUID.randomUUID());
    }
}
