package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.util.UUID;

/**
 * Backs GET /aircraft?search= - reused as-is by both the flight-entry picker (which only renders
 * id/registration/make/model) and the Browse Aircraft page (which renders everything else too);
 * see docs/DECISIONS.md's 2026-08-30 entry for why this isn't split into two DTOs. Every field
 * from engineCategory onwards is nullable - reference data seeded from OpenSky, not guaranteed
 * present on every row (see docs/plans/aircraft-picker.md).
 */
@OpenApiName("Aircraft")
public class AircraftDto {

    private final UUID id;
    private final String registration;
    private final String make;
    private final String model;
    private final String engineCategory;
    private final String manufacturerIcao;
    private final String typeCode;
    private final String serialNumber;
    private final String operator;
    private final String owner;
    private final Integer built;
    private final String engines;
    private final String categoryDescription;

    public AircraftDto(@JsonProperty("id") UUID id, @JsonProperty("registration") String registration,
                        @JsonProperty("make") String make, @JsonProperty("model") String model,
                        @JsonProperty("engineCategory") String engineCategory,
                        @JsonProperty("manufacturerIcao") String manufacturerIcao,
                        @JsonProperty("typeCode") String typeCode, @JsonProperty("serialNumber") String serialNumber,
                        @JsonProperty("operator") String operator, @JsonProperty("owner") String owner,
                        @JsonProperty("built") Integer built, @JsonProperty("engines") String engines,
                        @JsonProperty("categoryDescription") String categoryDescription) {
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

    public UUID getId() {
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

    public String getEngineCategory() {
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
}
