package com.bonney.hobbs.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.time.LocalDate;

/** A synthetic training device (FSTD) session - its own row shape in the CAA logbook, separate from FlightEntry. */
public class SimulatorSession {

    private final SimulatorSessionId id;
    private final PilotId pilotId;
    private final LocalDate date;
    private final String fstdType;
    private final int minutes;

    public SimulatorSession(SimulatorSessionId id, PilotId pilotId, LocalDate date, String fstdType, int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("minutes cannot be negative");
        }
        this.id = id;
        this.pilotId = pilotId;
        this.date = date;
        this.fstdType = fstdType;
        this.minutes = minutes;
    }

    public SimulatorSessionId getId() {
        return id;
    }

    public PilotId getPilotId() {
        return pilotId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getFstdType() {
        return fstdType;
    }

    public int getMinutes() {
        return minutes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimulatorSession that = (SimulatorSession) o;
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
                .append("date", date)
                .append("fstdType", fstdType)
                .append("minutes", minutes)
                .toString();
    }
}
