package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class AircraftTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        AircraftId id = AircraftId.random();
        Aircraft a = new Aircraft(id, "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);
        Aircraft b = new Aircraft(id, "G-WXYZ", "Piper", "PA-28", EngineCategory.MULTI_ENGINE);

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void aircraftWithDifferentIdsAreNotEqual() {
        Aircraft a = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);
        Aircraft b = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);

        assertThat(aircraft.equals(null), is(false));
        assertThat(aircraft.equals("not an aircraft"), is(false));
    }

    @Test
    void toStringIncludesTheRegistration() {
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);

        assertThat(aircraft.toString(), containsString("G-ABCD"));
    }
}
