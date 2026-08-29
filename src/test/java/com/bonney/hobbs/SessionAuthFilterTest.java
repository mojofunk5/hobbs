package com.bonney.hobbs;

import com.bonney.hobbs.domain.AdminRepository;
import com.bonney.hobbs.domain.Pilot;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.domain.SessionId;
import com.bonney.hobbs.domain.Sessions;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class SessionAuthFilterTest {

    @Mock
    Context context;

    @Mock
    AdminRepository adminRepository;

    @Mock
    Sessions sessions;

    SessionAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SessionAuthFilter(sessions, adminRepository);
    }

    @Test
    void optionsRequestsAreSkipped() {
        when(context.method()).thenReturn(HandlerType.OPTIONS);

        filter.handle(context);

        verify(context, never()).status(HttpStatus.UNAUTHORIZED);
        verify(context, never()).skipRemainingHandlers();
    }

    @Test
    void authPathsAreSkipped() {
        when(context.path()).thenReturn("/auth/login");

        filter.handle(context);

        verify(context, never()).status(HttpStatus.UNAUTHORIZED);
        verify(context, never()).skipRemainingHandlers();
    }

    @Test
    void healthPathIsSkipped() {
        when(context.path()).thenReturn("/health");

        filter.handle(context);

        verify(context, never()).status(HttpStatus.UNAUTHORIZED);
        verify(context, never()).skipRemainingHandlers();
    }

    @Test
    void openApiPathIsSkipped() {
        when(context.path()).thenReturn("/openapi");

        filter.handle(context);

        verify(context, never()).status(HttpStatus.UNAUTHORIZED);
        verify(context, never()).skipRemainingHandlers();
    }

    @Test
    void swaggerPathIsSkipped() {
        when(context.path()).thenReturn("/swagger");

        filter.handle(context);

        verify(context, never()).status(HttpStatus.UNAUTHORIZED);
        verify(context, never()).skipRemainingHandlers();
    }

    @Test
    void webjarsPathsAreSkipped() {
        when(context.path()).thenReturn("/webjars/swagger-ui/5.31.2/swagger-ui-bundle.js");

        filter.handle(context);

        verify(context, never()).status(HttpStatus.UNAUTHORIZED);
        verify(context, never()).skipRemainingHandlers();
    }

    @Test
    void missingHeaderReturnsUnauthorised() {
        when(context.path()).thenReturn("/game");
        when(context.header("Authorization")).thenReturn(null);
        when(context.status(HttpStatus.UNAUTHORIZED)).thenReturn(context);
        when(context.result(anyString())).thenReturn(context);

        filter.handle(context);

        verify(context).status(HttpStatus.UNAUTHORIZED);
        verify(context).skipRemainingHandlers();
    }

    @Test
    void malformedHeaderReturnsUnauthorised() {
        when(context.path()).thenReturn("/game");
        when(context.header("Authorization")).thenReturn("notabearer");
        when(context.status(HttpStatus.UNAUTHORIZED)).thenReturn(context);
        when(context.result(anyString())).thenReturn(context);

        filter.handle(context);

        verify(context).status(HttpStatus.UNAUTHORIZED);
        verify(context).skipRemainingHandlers();
    }

    @Test
    void unknownSessionReturnsUnauthorised() {
        when(context.path()).thenReturn("/game");
        when(context.header("Authorization")).thenReturn("Bearer " + UUID.randomUUID());
        when(context.status(HttpStatus.UNAUTHORIZED)).thenReturn(context);
        when(context.result(anyString())).thenReturn(context);

        filter.handle(context);

        verify(context).status(HttpStatus.UNAUTHORIZED);
        verify(context).skipRemainingHandlers();
    }

    @Test
    void validSessionPassesThrough() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", null);
        SessionId sessionId = SessionId.random();
        when(sessions.find(sessionId)).thenReturn(Optional.of(pilot.getId()));
        when(context.path()).thenReturn("/game");
        when(context.header("Authorization")).thenReturn("Bearer " + sessionId.value());

        filter.handle(context);

        verify(context, never()).status(HttpStatus.UNAUTHORIZED);
        verify(context, never()).skipRemainingHandlers();
        verify(context).attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID, pilot.getId());
    }

    @Test
    void nonAdminIsRejectedFromAdminPaths() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", null);
        SessionId sessionId = SessionId.random();
        when(sessions.find(sessionId)).thenReturn(Optional.of(pilot.getId()));
        when(context.path()).thenReturn("/admin/referral-code");
        when(context.header("Authorization")).thenReturn("Bearer " + sessionId.value());
        when(adminRepository.isAdmin(pilot.getId())).thenReturn(false);
        when(context.status(HttpStatus.FORBIDDEN)).thenReturn(context);
        when(context.result(anyString())).thenReturn(context);

        filter.handle(context);

        verify(context).status(HttpStatus.FORBIDDEN);
        verify(context).skipRemainingHandlers();
    }

    @Test
    void adminPassesThroughAdminPaths() {
        Pilot pilot = new Pilot(PilotId.random(), "Alice", null);
        SessionId sessionId = SessionId.random();
        when(sessions.find(sessionId)).thenReturn(Optional.of(pilot.getId()));
        when(context.path()).thenReturn("/admin/referral-code");
        when(context.header("Authorization")).thenReturn("Bearer " + sessionId.value());
        when(adminRepository.isAdmin(pilot.getId())).thenReturn(true);

        filter.handle(context);

        verify(context, never()).status(HttpStatus.FORBIDDEN);
        verify(context, never()).skipRemainingHandlers();
    }
}
