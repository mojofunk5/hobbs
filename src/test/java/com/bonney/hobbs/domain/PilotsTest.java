package com.bonney.hobbs.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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
    void createRejectsAnInvalidNameWithoutSavingThePilot() {
        assertThrows(InvalidNameException.class, () -> pilots.create("", null));

        verify(repository, never()).save(any());
    }

    @Test
    void updateNameRejectsAnInvalidNameWithoutSavingThePilot() {
        PilotId id = PilotId.random();

        assertThrows(InvalidNameException.class, () -> pilots.updateName(id, ""));

        verify(repository, never()).updateName(any(), any());
    }

    @Test
    void createWithNoCreatorProducesASelfRegisteredPilot() {
        pilots.create("Alice", null);

        var captor = org.mockito.ArgumentCaptor.forClass(Pilot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy(), is(nullValue()));
    }

    @Test
    void createWithACreatorProducesAnUnclaimedPilotRecordingWhoCreatedIt() {
        PilotId createdBy = PilotId.random();

        pilots.create("Louis", createdBy);

        var captor = org.mockito.ArgumentCaptor.forClass(Pilot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy(), is(createdBy));
    }
}
