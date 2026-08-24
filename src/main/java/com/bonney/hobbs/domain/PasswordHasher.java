package com.bonney.hobbs.domain;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    public String hash(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt());
    }

    public boolean verify(String plaintext, String hash) {
        return BCrypt.checkpw(plaintext, hash);
    }
}
