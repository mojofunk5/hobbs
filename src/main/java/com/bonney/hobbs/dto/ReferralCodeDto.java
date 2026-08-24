package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("ReferralCode")
public class ReferralCodeDto {

    private final String code;

    public ReferralCodeDto(@JsonProperty("code") String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
