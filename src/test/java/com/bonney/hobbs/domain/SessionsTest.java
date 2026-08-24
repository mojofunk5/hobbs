package com.bonney.hobbs.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Thin orchestration tests only - Sessions delegates the actual expiry/TTL math to
// SessionRepository, which is tested against a real (H2) database in SessionRepositoryTest.
@ExtendWith(MockitoExtension.class)
class SessionsTest {

    @Mock
    SessionRepository repository;

    Sessions sessions;

    Pilot alice = new Pilot(PilotId.random(), "Alice", "alice@example.com");

    @BeforeEach
    void setUp() {
        sessions = new Sessions(repository, 24);
    }

    @Test
    void createSavesANewSessionAndReturnsItForThePilot() {
        Session session = sessions.create(alice);

        assertThat(session.getSessionId(), is(notNullValue()));
        assertThat(session.getPilot(), is(alice));
        verify(repository).save(eq(session.getSessionId()), eq(alice.getId()), any());
    }

    @Test
    void findDelegatesToTheRepositoryWithTheConfiguredTtl() {
        SessionId sessionId = SessionId.random();
        when(repository.findIfUnexpiredAndTouch(sessionId, 24)).thenReturn(Optional.of(alice.getId()));

        Optional<PilotId> found = sessions.find(sessionId);

        assertThat(found, is(Optional.of(alice.getId())));
    }

    @Test
    void findReturnsEmptyWhenTheRepositoryFindsNothing() {
        SessionId sessionId = SessionId.random();
        when(repository.findIfUnexpiredAndTouch(sessionId, 24)).thenReturn(Optional.empty());

        assertThat(sessions.find(sessionId), is(Optional.empty()));
    }

    @Test
    void deleteAllForPilotDelegatesToTheRepository() {
        sessions.deleteAllForPilot(alice.getId());

        verify(repository).deleteAllForPilot(alice.getId());
    }
}
