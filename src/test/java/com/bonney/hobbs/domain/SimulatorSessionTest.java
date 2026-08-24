package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulatorSessionTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        SimulatorSessionId id = SimulatorSessionId.random();
        SimulatorSession a = new SimulatorSession(id, PilotId.random(), LocalDate.now(), "AATD", 60);
        SimulatorSession b = new SimulatorSession(id, PilotId.random(), LocalDate.now().minusDays(1), "FNPT", 30);

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void sessionsWithDifferentIdsAreNotEqual() {
        PilotId pilotId = PilotId.random();
        SimulatorSession a = new SimulatorSession(SimulatorSessionId.random(), pilotId, LocalDate.now(), "AATD", 60);
        SimulatorSession b = new SimulatorSession(SimulatorSessionId.random(), pilotId, LocalDate.now(), "AATD", 60);

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        SimulatorSession session = new SimulatorSession(SimulatorSessionId.random(), PilotId.random(), LocalDate.now(), "AATD", 60);

        assertThat(session.equals(null), is(false));
        assertThat(session.equals("not a session"), is(false));
    }

    @Test
    void negativeMinutesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatorSession(SimulatorSessionId.random(), PilotId.random(), LocalDate.now(), "AATD", -1));
    }
}
