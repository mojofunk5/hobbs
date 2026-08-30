package com.bonney.hobbs.domain;

public class InvalidAircraftSearchException extends RuntimeException {

    public InvalidAircraftSearchException(int minLength) {
        super("search is required and must be at least " + minLength + " characters");
    }
}
