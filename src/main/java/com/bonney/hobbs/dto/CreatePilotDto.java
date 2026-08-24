package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("CreatePilot")
public class CreatePilotDto {

    private final String name;
    private final String email;

    @JsonCreator
    public CreatePilotDto(@JsonProperty("name") String name, @JsonProperty("email") String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
