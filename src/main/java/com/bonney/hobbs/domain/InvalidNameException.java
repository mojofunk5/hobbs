package com.bonney.hobbs.domain;

public class InvalidNameException extends RuntimeException {

    public InvalidNameException(String name) {
        super("Not a valid name: " + name);
    }
}
