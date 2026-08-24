package com.bonney.hobbs.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * A raw GPS recording of a flight, captured by the mobile app while William has "record" active.
 * Deliberately stored as a single JSON blob of points (see FlightTrackRepository) rather than one
 * row per point - a normalized point table would be the right call once querying/analysing
 * individual points server-side is actually needed, but for the MVP the whole track is only ever
 * read back as a unit (to derive a draft FlightEntry, or to redraw the route on a map) and a
 * per-point table would just be premature schema complexity.
 *
 * A track always belongs to a pilot but is only ever linked to a FlightEntry once that entry is
 * created from it (see FlightEntry.getFlightTrackId) - recording can happen, and can even fail
 * partway through, entirely independently of whether a logbook entry ever gets written from it.
 */
public class FlightTrack {

    private final FlightTrackId id;
    private final PilotId pilotId;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime endedAt;
    private final String pointsJson;

    public FlightTrack(FlightTrackId id, PilotId pilotId, OffsetDateTime startedAt, OffsetDateTime endedAt,
                        String pointsJson) {
        this.id = id;
        this.pilotId = pilotId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.pointsJson = pointsJson;
    }

    public FlightTrackId getId() {
        return id;
    }

    public PilotId getPilotId() {
        return pilotId;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    /** Empty until the recording finishes and uploads - a track can exist mid-flight with no end yet. */
    public Optional<OffsetDateTime> getEndedAt() {
        return Optional.ofNullable(endedAt);
    }

    public String getPointsJson() {
        return pointsJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FlightTrack that = (FlightTrack) o;
        return new EqualsBuilder().append(id, that.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("pilotId", pilotId)
                .append("startedAt", startedAt)
                .append("endedAt", endedAt)
                .toString();
    }
}
