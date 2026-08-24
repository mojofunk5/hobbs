package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void acceptsAPasswordMeetingAllRules() {
        PasswordPolicy.validate("Pass1word");
        PasswordPolicy.validate("Str0ng!Pass");
        PasswordPolicy.validate("Abcdefg1");
        PasswordPolicy.validate("A1bcdefg");
    }

    @Test
    void rejectsNullPassword() {
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate(null));
    }

    @Test
    void rejectsPasswordsShorterThan8Characters() {
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate("Ab1"));
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate("Short1!"));
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate("Ab1defg"));
    }

    @Test
    void rejectsPasswordsMissingUpperOrLowerCase() {
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate("alllowercase1"));
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate("ALLUPPERCASE1"));
    }

    @Test
    void rejectsPasswordWithNoDigitOrSpecialCharacter() {
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate("NoDigitsHere"));
    }

    @Test
    void acceptsASpecialCharacterInPlaceOfADigit() {
        PasswordPolicy.validate("NoDigits!Here");
    }

    @Test
    void acceptsAPasswordAtExactly72Characters() {
        PasswordPolicy.validate("Ab1" + "a".repeat(69));
    }

    @Test
    void rejectsAPasswordLongerThan72Characters() {
        assertThrows(InvalidPasswordException.class, () -> PasswordPolicy.validate("Ab1" + "a".repeat(70)));
    }
}
