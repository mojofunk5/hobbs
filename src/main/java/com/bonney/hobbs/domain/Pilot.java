package com.bonney.hobbs.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Pilot {

    private final PilotId id;
    private final String name;
    private final PilotId createdBy;

    public Pilot(PilotId id, String name, PilotId createdBy) {
        this.id = id;
        this.name = name;
        this.createdBy = createdBy;
    }

    public PilotId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Who created this {@code Pilot} record - {@code null} for a self-registered pilot, or the
     * inviting pilot's ID for a still-unclaimed record created on someone else's behalf (e.g. a
     * co-pilot logged before they've signed up).
     */
    public PilotId getCreatedBy() {
        return createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pilot that = (Pilot) o;
        return new EqualsBuilder()
                .append(id, that.id)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(id)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("name", name)
                .append("createdBy", createdBy)
                .toString();
    }
}
