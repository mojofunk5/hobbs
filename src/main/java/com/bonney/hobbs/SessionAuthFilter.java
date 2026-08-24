package com.bonney.hobbs;

import com.bonney.hobbs.domain.AdminRepository;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.domain.SessionId;
import com.bonney.hobbs.domain.Sessions;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SessionAuthFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/version", "/openapi", "/swagger");

    private final Sessions sessions;
    private final AdminRepository adminRepository;

    public SessionAuthFilter(Sessions sessions, AdminRepository adminRepository) {
        this.sessions = sessions;
        this.adminRepository = adminRepository;
    }

    public static final String AUTHENTICATED_PILOT_ID = "authenticatedPilotId";

    public void handle(Context context) {
        if (context.method() == HandlerType.OPTIONS) {
            // CORS preflight requests never carry credentials (browsers strip them by design) - they
            // aren't a real API call, just the browser checking permission before sending one. Let
            // Javalin's CORS plugin answer these; don't reject them for lacking an Authorization header.
            return;
        }

        String path = context.path();
        if (path.startsWith("/auth/") || path.startsWith("/webjars/") || PUBLIC_PATHS.contains(path)) {
            return;
        }

        String header = context.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            context.status(HttpStatus.UNAUTHORIZED).result("Missing or invalid Authorization header").skipRemainingHandlers();
            return;
        }

        UUID sessionUuid;
        try {
            sessionUuid = UUID.fromString(header.substring("Bearer ".length()).trim());
        } catch (IllegalArgumentException e) {
            context.status(HttpStatus.UNAUTHORIZED).result("Invalid session ID").skipRemainingHandlers();
            return;
        }

        Optional<PilotId> pilotId = sessions.find(SessionId.from(sessionUuid));
        if (pilotId.isEmpty()) {
            context.status(HttpStatus.UNAUTHORIZED).result("Session not found").skipRemainingHandlers();
            return;
        }

        context.attribute(AUTHENTICATED_PILOT_ID, pilotId.get());

        if (path.startsWith("/admin/") && !adminRepository.isAdmin(pilotId.get())) {
            context.status(HttpStatus.FORBIDDEN).result("Admin access required").skipRemainingHandlers();
        }
    }
}
