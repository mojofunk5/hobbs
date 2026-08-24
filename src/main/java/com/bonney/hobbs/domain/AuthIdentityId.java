package com.bonney.hobbs.domain;

import java.util.UUID;

public final class AuthIdentityId extends TypedId {

    private AuthIdentityId(UUID value) {
        super(value);
    }

    public static AuthIdentityId from(UUID value) {
        return new AuthIdentityId(value);
    }

    public static AuthIdentityId random() {
        return new AuthIdentityId(UUID.randomUUID());
    }
}
