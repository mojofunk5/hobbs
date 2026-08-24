package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailValidatorTest {

    @Test
    void acceptsAWellFormedEmail() {
        EmailValidator.validate("alice@example.com");
        EmailValidator.validate("alice.smith+test@sub.example.co.uk");
    }

    @Test
    void rejectsNullEmail() {
        assertThrows(InvalidEmailException.class, () -> EmailValidator.validate(null));
    }

    @Test
    void rejectsAnEmailWithNoAtSign() {
        assertThrows(InvalidEmailException.class, () -> EmailValidator.validate("alice.example.com"));
    }

    @Test
    void rejectsAnEmailWithNoDomain() {
        assertThrows(InvalidEmailException.class, () -> EmailValidator.validate("alice@"));
    }

    @Test
    void rejectsAnEmailWithNoTld() {
        assertThrows(InvalidEmailException.class, () -> EmailValidator.validate("alice@example"));
    }

    @Test
    void rejectsAnEmailContainingWhitespace() {
        assertThrows(InvalidEmailException.class, () -> EmailValidator.validate("alice @example.com"));
    }

    @Test
    void acceptsAnEmailAtExactly255Characters() {
        String local = "a".repeat(255 - "@example.com".length());
        EmailValidator.validate(local + "@example.com");
    }

    @Test
    void rejectsAnEmailLongerThan255Characters() {
        String local = "a".repeat(256 - "@example.com".length());
        assertThrows(InvalidEmailException.class, () -> EmailValidator.validate(local + "@example.com"));
    }
}
