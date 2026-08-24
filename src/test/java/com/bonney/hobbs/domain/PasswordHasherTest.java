package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashIsNotPlaintext() {
        assertThat(hasher.hash("secret"), is(not("secret")));
    }

    @Test
    void correctPasswordVerifies() {
        String hash = hasher.hash("secret");
        assertThat(hasher.verify("secret", hash), is(true));
    }

    @Test
    void wrongPasswordDoesNotVerify() {
        String hash = hasher.hash("secret");
        assertThat(hasher.verify("wrong", hash), is(false));
    }

    @Test
    void twoHashesOfSamePasswordAreDifferent() {
        assertThat(hasher.hash("secret"), is(not(hasher.hash("secret"))));
    }
}
