package com.bonney.hobbs.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountsTest {

    @Mock
    AccountRepository repository;

    @Mock
    AuthIdentityRepository authIdentityRepository;

    Accounts accounts;

    PilotId pilotId = PilotId.random();

    @BeforeEach
    void setUp() {
        accounts = new Accounts(repository, authIdentityRepository);
    }

    @Test
    void createRejectsAnInvalidEmailWithoutSaving() {
        assertThrows(InvalidEmailException.class, () -> accounts.create(pilotId, "not-an-email"));

        verify(repository, never()).create(any(), any());
    }

    @Test
    void createDelegatesToRepository() {
        accounts.create(pilotId, "alice@example.com");

        verify(repository).create(pilotId, "alice@example.com");
    }

    @Test
    void updateEmailRejectsAnInvalidEmailWithoutSaving() {
        assertThrows(InvalidEmailException.class, () -> accounts.updateEmail(pilotId, "not-an-email"));

        verify(repository, never()).updateEmail(any(), any());
        verify(authIdentityRepository, never()).updateIdentifier(any(), any(), any());
    }

    @Test
    void updateEmailAlsoUpdatesThePasswordAuthIdentityIdentifier() {
        accounts.updateEmail(pilotId, "new@example.com");

        verify(repository).updateEmail(pilotId, "new@example.com");
        verify(authIdentityRepository).updateIdentifier(pilotId, AuthIdentityType.PASSWORD, "new@example.com");
    }

    @Test
    void disableDelegatesToRepository() {
        accounts.disable(pilotId);

        verify(repository).disable(pilotId);
    }

    @Test
    void enableDelegatesToRepository() {
        accounts.enable(pilotId);

        verify(repository).enable(pilotId);
    }

    @Test
    void isDisabledDelegatesToRepository() {
        when(repository.isDisabled(pilotId)).thenReturn(true);

        assertThat(accounts.isDisabled(pilotId), is(true));
    }

    @Test
    void findByEmailDelegatesToRepository() {
        Account account = new Account(pilotId, "alice@example.com", false);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(account));

        assertThat(accounts.findByEmail("alice@example.com"), is(Optional.of(account)));
    }

    @Test
    void getDelegatesToRepository() {
        Account account = new Account(pilotId, "alice@example.com", false);
        when(repository.get(pilotId)).thenReturn(Optional.of(account));

        assertThat(accounts.get(pilotId), is(Optional.of(account)));
    }
}
