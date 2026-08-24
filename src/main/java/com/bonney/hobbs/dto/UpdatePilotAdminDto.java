package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("UpdatePilotAdmin")
public class UpdatePilotAdminDto {

    private final Boolean enabled;

    @JsonCreator
    public UpdatePilotAdminDto(@JsonProperty("enabled") Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Null means the field wasn't present in the request body - leave that aspect of the pilot
     * untouched, rather than treating absence as false. Partial update, not a full replace.
     */
    public Boolean getEnabled() {
        return enabled;
    }
}
