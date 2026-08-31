package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightEntryTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        FlightEntryId id = FlightEntryId.random();
        FlightEntry a = anEntry(id, null);
        FlightEntry b = anEntry(id, FlightTrackId.random());

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void entriesWithDifferentIdsAreNotEqual() {
        FlightEntry a = anEntry(FlightEntryId.random(), null);
        FlightEntry b = anEntry(FlightEntryId.random(), null);

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        FlightEntry entry = anEntry(FlightEntryId.random(), null);

        assertThat(entry.equals(null), is(false));
        assertThat(entry.equals("not an entry"), is(false));
    }

    @Test
    void negativeTotalMinutesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FlightEntry(FlightEntryId.random(),
                PilotId.random(), AircraftId.random(), null, LocalDate.now(), OffsetDateTime.now(),
                OffsetDateTime.now(), AirfieldId.random(), AirfieldId.random(), PilotId.random(), null,
                HolderOperatingCapacity.PILOT_IN_COMMAND, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, null));
    }

    @Test
    void flightTrackIdIsEmptyWhenManuallyEntered() {
        FlightEntry entry = anEntry(FlightEntryId.random(), null);

        assertThat(entry.getFlightTrackId(), is(Optional.empty()));
    }

    @Test
    void flightTrackIdIsPresentWhenDerivedFromARecording() {
        FlightTrackId trackId = FlightTrackId.random();
        FlightEntry entry = anEntry(FlightEntryId.random(), trackId);

        assertThat(entry.getFlightTrackId(), is(Optional.of(trackId)));
    }

    @Test
    void departureAndArrivalAirfieldIdAreTheOnesGiven() {
        AirfieldId departureAirfieldId = AirfieldId.random();
        AirfieldId arrivalAirfieldId = AirfieldId.random();
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), PilotId.random(), AircraftId.random(), null,
                LocalDate.now(), OffsetDateTime.now(), OffsetDateTime.now(), departureAirfieldId,
                arrivalAirfieldId, PilotId.random(), null, HolderOperatingCapacity.PILOT_IN_COMMAND,
                30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);

        assertThat(entry.getDepartureAirfieldId(), is(departureAirfieldId));
        assertThat(entry.getArrivalAirfieldId(), is(arrivalAirfieldId));
    }

    @Test
    void holderOperatingCapacityIsTheOneGiven() {
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), PilotId.random(), AircraftId.random(), null,
                LocalDate.now(), OffsetDateTime.now(), OffsetDateTime.now(), AirfieldId.random(),
                AirfieldId.random(), PilotId.random(), null, HolderOperatingCapacity.PILOT_UNDER_TRAINING,
                30, 0, 30, 0, 0, 0, 0, 0, 30, 0, 1, 0, null);

        assertThat(entry.getHolderOperatingCapacity(), is(HolderOperatingCapacity.PILOT_UNDER_TRAINING));
    }

    private FlightEntry anEntry(FlightEntryId id, FlightTrackId flightTrackId) {
        return new FlightEntry(id, PilotId.random(), AircraftId.random(), flightTrackId, LocalDate.now(),
                OffsetDateTime.now(), OffsetDateTime.now(), AirfieldId.random(), AirfieldId.random(),
                PilotId.random(), null, HolderOperatingCapacity.PILOT_IN_COMMAND, 30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
    }
}
