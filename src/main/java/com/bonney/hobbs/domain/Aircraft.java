package com.bonney.hobbs.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Aircraft {

    private final AircraftId id;
    private final String registration;
    private final String make;
    private final String model;
    private final EngineCategory engineCategory;

    public Aircraft(AircraftId id, String registration, String make, String model, EngineCategory engineCategory) {
        this.id = id;
        this.registration = registration;
        this.make = make;
        this.model = model;
        this.engineCategory = engineCategory;
    }

    public AircraftId getId() {
        return id;
    }

    public String getRegistration() {
        return registration;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public EngineCategory getEngineCategory() {
        return engineCategory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Aircraft that = (Aircraft) o;
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
                .append("registration", registration)
                .append("make", make)
                .append("model", model)
                .append("engineCategory", engineCategory)
                .toString();
    }
}
