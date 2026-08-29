package com.bonney.hobbs;

import com.bonney.hobbs.domain.Pilot;
import com.bonney.hobbs.domain.PilotId;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

public class TestPilots {

    public static Pilot randomPilot() {
        return new Pilot(PilotId.random(), randomAlphanumeric(10), null);
    }

    public static Pilot randomPilot(PilotId createdBy) {
        return new Pilot(PilotId.random(), randomAlphanumeric(10), createdBy);
    }
}
