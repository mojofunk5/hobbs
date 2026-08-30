package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class AirfieldTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        AirfieldId id = AirfieldId.random();
        Airfield a = anAirfield(id, "EGCJ", "Sherburn-in-Elmet Airfield");
        Airfield b = anAirfield(id, "EGNM", "Leeds Bradford Airport");

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void airfieldsWithDifferentIdsAreNotEqual() {
        Airfield a = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield");
        Airfield b = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield");

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        Airfield airfield = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield");

        assertThat(airfield.equals(null), is(false));
        assertThat(airfield.equals("not an airfield"), is(false));
    }

    @Test
    void toStringIncludesTheName() {
        Airfield airfield = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield");

        assertThat(airfield.toString(), containsString("Sherburn-in-Elmet Airfield"));
    }

    @Test
    void icaoCodeCanBeNull() {
        // Some small GB strips in the OurAirports dataset genuinely have no ICAO code.
        Airfield airfield = new Airfield(AirfieldId.random(), null, "Some Farm Strip", "Nowhere", "GB", "GB-ENG",
                53.8, -1.2, 50, "small_airport", "ourairports", "12345");

        assertThat(airfield.getIcaoCode(), is((String) null));
    }

    private static Airfield anAirfield(AirfieldId id, String icaoCode, String name) {
        return new Airfield(id, icaoCode, name, "Sherburn-in-Elmet", "GB", "GB-ENG", 53.7883, -1.2225, 26,
                "small_airport", "ourairports", "12345");
    }
}
