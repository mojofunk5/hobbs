package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.util.UUID;

@OpenApiName("PilotSummary")
public class PilotSummaryDto {

    private final UUID id;
    private final String name;

    public PilotSummaryDto(@JsonProperty("id") UUID id, @JsonProperty("name") String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
