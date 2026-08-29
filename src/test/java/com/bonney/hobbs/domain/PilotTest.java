package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class PilotTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        PilotId id = PilotId.random();
        Pilot a = new Pilot(id, "Alice", null);
        Pilot b = new Pilot(id, "Different Name", PilotId.random());

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void pilotsWithDifferentIdsAreNotEqual() {
        Pilot a = new Pilot(PilotId.random(), "Alice", null);
        Pilot b = new Pilot(PilotId.random(), "Alice", null);

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", null);

        assertThat(pilot.equals(null), is(false));
        assertThat(pilot.equals("not a pilot"), is(false));
    }

    @Test
    void selfRegisteredPilotHasNoCreatedBy() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", null);

        assertThat(pilot.getCreatedBy(), is(nullValue()));
    }

    @Test
    void anUnclaimedPilotCarriesWhoCreatedIt() {
        PilotId createdBy = PilotId.random();
        Pilot pilot = new Pilot(PilotId.random(), "Louis", createdBy);

        assertThat(pilot.getCreatedBy(), is(createdBy));
    }

    @Test
    void toStringIncludesTheName() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", null);

        assertThat(pilot.toString(), containsString("Alice"));
    }
}
