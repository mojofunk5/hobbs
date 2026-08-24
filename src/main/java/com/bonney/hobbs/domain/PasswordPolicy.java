package com.bonney.hobbs.domain;

/**
 * Enforced only at registration - existing passwords set before this policy existed keep working at
 * login, and only have to comply the next time that pilot sets a new one.
 */
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    // jBCrypt silently truncates input beyond 72 bytes, so two different passwords sharing the first
    // 72 bytes would otherwise hash identically - capping the length here surfaces that as a clear
    // rejection instead of a silent, surprising collision.
    private static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
        super();
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new InvalidPasswordException("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new InvalidPasswordException("Password must be no more than " + MAX_LENGTH + " characters long.");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        if (!hasUpper || !hasLower) {
            throw new InvalidPasswordException("Password must contain both upper and lower case letters.");
        }
        boolean hasDigitOrSpecial = password.chars().anyMatch(c -> Character.isDigit(c) || !Character.isLetterOrDigit(c));
        if (!hasDigitOrSpecial) {
            throw new InvalidPasswordException("Password must contain at least one number or special character.");
        }
    }
}
