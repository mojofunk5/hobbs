package com.bonney.hobbs.domain;

public class InvalidPasswordResetCodeException extends RuntimeException {

    public InvalidPasswordResetCodeException() {
        super("Invalid, expired, or already used password reset code");
    }
}
