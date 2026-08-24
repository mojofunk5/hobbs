package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.util.UUID;

@OpenApiName("Aircraft")
public class AircraftDto {

    private final UUID id;
    private final String registration;
    private final String make;
    private final String model;
    private final String engineCategory;

    public AircraftDto(@JsonProperty("id") UUID id, @JsonProperty("registration") String registration,
                        @JsonProperty("make") String make, @JsonProperty("model") String model,
                        @JsonProperty("engineCategory") String engineCategory) {
        this.id = id;
        this.registration = registration;
        this.make = make;
        this.model = model;
        this.engineCategory = engineCategory;
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
}
