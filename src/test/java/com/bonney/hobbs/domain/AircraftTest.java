package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class AircraftTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        AircraftId id = AircraftId.random();
        Aircraft a = anAircraft(id, "G-ABCD", EngineCategory.SINGLE_ENGINE);
        Aircraft b = anAircraft(id, "G-WXYZ", EngineCategory.MULTI_ENGINE);

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void aircraftWithDifferentIdsAreNotEqual() {
        Aircraft a = anAircraft(AircraftId.random(), "G-ABCD", EngineCategory.SINGLE_ENGINE);
        Aircraft b = anAircraft(AircraftId.random(), "G-ABCD", EngineCategory.SINGLE_ENGINE);

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        Aircraft aircraft = anAircraft(AircraftId.random(), "G-ABCD", EngineCategory.SINGLE_ENGINE);

        assertThat(aircraft.equals(null), is(false));
        assertThat(aircraft.equals("not an aircraft"), is(false));
    }

    @Test
    void toStringIncludesTheRegistration() {
        Aircraft aircraft = anAircraft(AircraftId.random(), "G-ABCD", EngineCategory.SINGLE_ENGINE);

        assertThat(aircraft.toString(), containsString("G-ABCD"));
    }

    @Test
    void engineCategoryAndReferenceFieldsCanAllBeNull() {
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", null,
                null, null, null, null, null, null, null, null);

        assertThat(aircraft.getEngineCategory(), is((EngineCategory) null));
        assertThat(aircraft.getManufacturerIcao(), is((String) null));
        assertThat(aircraft.getBuilt(), is((Integer) null));
    }

    private static Aircraft anAircraft(AircraftId id, String registration, EngineCategory engineCategory) {
        return new Aircraft(id, registration, "Cessna", "152", engineCategory,
                null, null, null, null, null, null, null, null);
    }
}
