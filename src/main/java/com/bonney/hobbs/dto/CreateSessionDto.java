package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("CreateSession")
public class CreateSessionDto {

    private final String email;

    @JsonCreator
    public CreateSessionDto(@JsonProperty("email") String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
