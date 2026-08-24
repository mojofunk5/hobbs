package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("Register")
public class RegisterDto {

    private final String name;
    private final String email;
    private final String password;
    private final String referralCode;

    @JsonCreator
    public RegisterDto(
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("password") String password,
            @JsonProperty("referralCode") String referralCode) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.referralCode = referralCode;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getReferralCode() {
        return referralCode;
    }
}
