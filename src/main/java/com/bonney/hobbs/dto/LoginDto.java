package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("Login")
public class LoginDto {

    private final String identifier;
    private final String password;

    @JsonCreator
    public LoginDto(
            @JsonProperty("identifier") String identifier,
            @JsonProperty("password") String password) {
        this.identifier = identifier;
        this.password = password;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getPassword() {
        return password;
    }
}
