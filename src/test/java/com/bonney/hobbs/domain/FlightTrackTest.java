package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FlightTrackTest {

    @Test
    void equalityIsBasedOnIdAlone() {
        FlightTrackId id = FlightTrackId.random();
        PilotId pilotId = PilotId.random();
        FlightTrack a = new FlightTrack(id, pilotId, OffsetDateTime.now(), null, "[]");
        FlightTrack b = new FlightTrack(id, PilotId.random(), OffsetDateTime.now().plusHours(1), OffsetDateTime.now(), "[{}]");

        assertThat(a.equals(b), is(true));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void tracksWithDifferentIdsAreNotEqual() {
        PilotId pilotId = PilotId.random();
        OffsetDateTime startedAt = OffsetDateTime.now();
        FlightTrack a = new FlightTrack(FlightTrackId.random(), pilotId, startedAt, null, "[]");
        FlightTrack b = new FlightTrack(FlightTrackId.random(), pilotId, startedAt, null, "[]");

        assertThat(a.equals(b), is(false));
    }

    @Test
    void notEqualToNullOrADifferentType() {
        FlightTrack track = new FlightTrack(FlightTrackId.random(), PilotId.random(), OffsetDateTime.now(), null, "[]");

        assertThat(track.equals(null), is(false));
        assertThat(track.equals("not a track"), is(false));
    }

    @Test
    void endedAtIsEmptyWhileStillRecording() {
        FlightTrack track = new FlightTrack(FlightTrackId.random(), PilotId.random(), OffsetDateTime.now(), null, "[]");

        assertThat(track.getEndedAt(), is(Optional.empty()));
    }

    @Test
    void endedAtIsPresentOnceTheRecordingFinishes() {
        OffsetDateTime endedAt = OffsetDateTime.now();
        FlightTrack track = new FlightTrack(FlightTrackId.random(), PilotId.random(), endedAt.minusMinutes(45), endedAt, "[]");

        assertThat(track.getEndedAt(), is(Optional.of(endedAt)));
    }
}
