package com.bonney.hobbs.domain;

public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException(String email) {
        super("Not a valid email address: " + email);
    }
}
