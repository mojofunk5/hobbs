package com.bonney.hobbs.domain;

public class InvalidReferralCodeException extends RuntimeException {

    public InvalidReferralCodeException() {
        super("Invalid or already used referral code");
    }
}
