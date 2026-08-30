package com.bonney.hobbs.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Reference data seeded from OpenSky's aircraftDatabase.csv (see docs/plans/aircraft-picker.md),
 * not pilot-submitted - registration/make/model are the fields this repo had before that plan,
 * everything from manufacturerIcao onwards mirrors an OpenSky CSV column directly.
 * engineCategory and every field from manufacturerIcao onwards are nullable: derived where
 * parseable (engineCategory from OpenSky's icaoaircrafttype) or simply absent from a given CSV
 * row, rather than guessed.
 */
public class Aircraft {

    private final AircraftId id;
    private final String registration;
    private final String make;
    private final String model;
    private final EngineCategory engineCategory;
    private final String manufacturerIcao;
    private final String typeCode;
    private final String serialNumber;
    private final String operator;
    private final String owner;
    private final Integer built;
    private final String engines;
    private final String categoryDescription;

    public Aircraft(AircraftId id, String registration, String make, String model, EngineCategory engineCategory,
                     String manufacturerIcao, String typeCode, String serialNumber, String operator, String owner,
                     Integer built, String engines, String categoryDescription) {
        this.id = id;
        this.registration = registration;
        this.make = make;
        this.model = model;
        this.engineCategory = engineCategory;
        this.manufacturerIcao = manufacturerIcao;
        this.typeCode = typeCode;
        this.serialNumber = serialNumber;
        this.operator = operator;
        this.owner = owner;
        this.built = built;
        this.engines = engines;
        this.categoryDescription = categoryDescription;
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

    public String getManufacturerIcao() {
        return manufacturerIcao;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getOperator() {
        return operator;
    }

    public String getOwner() {
        return owner;
    }

    public Integer getBuilt() {
        return built;
    }

    public String getEngines() {
        return engines;
    }

    public String getCategoryDescription() {
        return categoryDescription;
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
