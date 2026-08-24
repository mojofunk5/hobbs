package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("PasswordResetConfirm")
public class PasswordResetConfirmDto {

    private final String email;
    private final String code;
    private final String newPassword;

    @JsonCreator
    public PasswordResetConfirmDto(
            @JsonProperty("email") String email,
            @JsonProperty("code") String code,
            @JsonProperty("newPassword") String newPassword) {
        this.email = email;
        this.code = code;
        this.newPassword = newPassword;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }

    public String getNewPassword() {
        return newPassword;
    }
}
