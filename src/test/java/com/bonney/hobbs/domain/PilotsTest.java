package com.bonney.hobbs.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PilotsTest {

    @Mock
    PilotRepository repository;

    Pilots pilots;

    @BeforeEach
    void setUp() {
        pilots = new Pilots(repository);
    }

    @Test
    void createRejectsAnInvalidEmailWithoutSavingThePilot() {
        assertThrows(InvalidEmailException.class, () -> pilots.create("Alice", "not-an-email"));

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsAnInvalidEmailWithoutSavingThePilot() {
        PilotId id = PilotId.random();

        assertThrows(InvalidEmailException.class, () -> pilots.update(id, "Alice", "not-an-email"));

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsAnInvalidNameWithoutSavingThePilot() {
        assertThrows(InvalidNameException.class, () -> pilots.create("", "alice@example.com"));

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsAnInvalidNameWithoutSavingThePilot() {
        PilotId id = PilotId.random();

        assertThrows(InvalidNameException.class, () -> pilots.update(id, "", "alice@example.com"));

        verify(repository, never()).save(any());
    }
}
