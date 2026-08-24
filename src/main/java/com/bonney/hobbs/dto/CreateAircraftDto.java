package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateAircraftDto {

    private final String registration;
    private final String make;
    private final String model;
    private final String engineCategory;

    public CreateAircraftDto(@JsonProperty("registration") String registration, @JsonProperty("make") String make,
                              @JsonProperty("model") String model, @JsonProperty("engineCategory") String engineCategory) {
        this.registration = registration;
        this.make = make;
        this.model = model;
        this.engineCategory = engineCategory;
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
