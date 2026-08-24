package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("Version")
public class VersionDto {

    private final String sha;

    public VersionDto(@JsonProperty("sha") String sha) {
        this.sha = sha;
    }

    public String getSha() {
        return sha;
    }
}
