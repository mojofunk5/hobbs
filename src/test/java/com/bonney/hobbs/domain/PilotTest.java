package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class PilotTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        PilotId id = PilotId.random();
        Pilot a = new Pilot(id, "Alice", "alice@example.com");
        Pilot b = new Pilot(id, "Different Name", "different@example.com", true);

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void pilotsWithDifferentIdsAreNotEqual() {
        Pilot a = new Pilot(PilotId.random(), "Alice", "alice@example.com");
        Pilot b = new Pilot(PilotId.random(), "Alice", "alice@example.com");

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", "alice@example.com");

        assertThat(pilot.equals(null), is(false));
        assertThat(pilot.equals("not a pilot"), is(false));
    }

    @Test
    void defaultConstructorIsNotDisabled() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", "alice@example.com");

        assertThat(pilot.isDisabled(), is(false));
    }

    @Test
    void toStringIncludesTheNameAndEmail() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", "alice@example.com");

        assertThat(pilot.toString(), containsString("Alice"));
        assertThat(pilot.toString(), containsString("alice@example.com"));
    }
}
