package com.bonney.hobbs.domain;

import java.util.UUID;

public final class PasswordResetCodeId extends TypedId {

    private PasswordResetCodeId(UUID value) {
        super(value);
    }

    public static PasswordResetCodeId from(UUID value) {
        return new PasswordResetCodeId(value);
    }

    public static PasswordResetCodeId random() {
        return new PasswordResetCodeId(UUID.randomUUID());
    }
}
