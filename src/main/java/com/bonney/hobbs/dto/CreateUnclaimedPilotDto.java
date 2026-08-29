package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("CreateUnclaimedPilot")
public class CreateUnclaimedPilotDto {

    private final String name;

    @JsonCreator
    public CreateUnclaimedPilotDto(@JsonProperty("name") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
