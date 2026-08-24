package com.bonney.hobbs.domain;

import java.util.regex.Pattern;

public class EmailValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_LENGTH = 255;

    private EmailValidator() {
        super();
    }

    public static void validate(String email) {
        if (email == null || email.length() > MAX_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidEmailException(email);
        }
    }
}
