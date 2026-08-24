package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NameValidatorTest {

    @Test
    void acceptsAnOrdinaryName() {
        NameValidator.validate("Alice");
        NameValidator.validate("A");
        NameValidator.validate("a".repeat(50));
    }

    @Test
    void rejectsNullName() {
        assertThrows(InvalidNameException.class, () -> NameValidator.validate(null));
    }

    @Test
    void rejectsAnEmptyName() {
        assertThrows(InvalidNameException.class, () -> NameValidator.validate(""));
    }

    @Test
    void rejectsAWhitespaceOnlyName() {
        assertThrows(InvalidNameException.class, () -> NameValidator.validate("   "));
    }

    @Test
    void rejectsANameLongerThan50Characters() {
        assertThrows(InvalidNameException.class, () -> NameValidator.validate("a".repeat(51)));
    }

    @Test
    void ignoresLeadingAndTrailingWhitespaceWhenCheckingLength() {
        NameValidator.validate("  " + "a".repeat(50) + "  ");
    }
}
