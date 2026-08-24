package com.bonney.hobbs.domain;

public class NameValidator {

    private static final int MAX_LENGTH = 50;

    private NameValidator() {
        super();
    }

    public static void validate(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > MAX_LENGTH) {
            throw new InvalidNameException(name);
        }
    }
}
