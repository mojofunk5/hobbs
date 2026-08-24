package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.SessionAuthFilter;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.domain.Pilots;
import com.bonney.hobbs.dto.CreatePilotDto;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PilotEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(PilotEndpoint.class);

    private final Pilots pilots;

    public PilotEndpoint(Pilots pilots) {
        this.pilots = pilots;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.put("pilot/{pilotId}", this::updatePilot);
        config.routes.delete("pilot/{pilotId}", this::deletePilot);
    }

    @OpenApi(
        path = "/pilot/{pilotId}",
        methods = HttpMethod.PUT,
        summary = "Update a pilot",
        description = "Updates a pilot's name and email.",
        tags = {"Pilot"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(
                from = CreatePilotDto.class,
                example = "{\"name\": \"Alice Smith\", \"email\": \"alice@example.com\"}"
            )
        ),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "400", description = "Email is not a valid address"),
            @OpenApiResponse(status = "403", description = "Not the authenticated pilot"),
            @OpenApiResponse(status = "404", description = "Pilot not found")
        }
    )
    private void updatePilot(Context context) {
        PilotId pilotId = getPilotId(context);
        if (!isAuthenticatedPilot(context, pilotId)) {
            context.status(HttpStatus.FORBIDDEN);
            return;
        }
        CreatePilotDto request = context.bodyAsClass(CreatePilotDto.class);
        logger.info("Updating pilot with pilotId={} to name={}", pilotId, request.getName());
        if (pilots.get(pilotId).isEmpty()) {
            context.status(HttpStatus.NOT_FOUND);
            return;
        }
        pilots.update(pilotId, request.getName(), request.getEmail());
    }

    @OpenApi(
        path = "/pilot/{pilotId}",
        methods = HttpMethod.DELETE,
        summary = "Delete a pilot",
        description = "Deletes a pilot by ID.",
        tags = {"Pilot"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "403", description = "Not the authenticated pilot")
        }
    )
    private void deletePilot(Context context) {
        PilotId pilotId = getPilotId(context);
        if (!isAuthenticatedPilot(context, pilotId)) {
            context.status(HttpStatus.FORBIDDEN);
            return;
        }
        logger.info("Deleting pilot with pilotId={}", pilotId);
        pilots.delete(pilotId);
    }

    private boolean isAuthenticatedPilot(Context context, PilotId pilotId) {
        PilotId authenticatedPilotId = context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
        return pilotId.equals(authenticatedPilotId);
    }

    private PilotId getPilotId(Context context) {
        return PilotId.from(context.pathParamAsClass("pilotId", UUID.class).get());
    }
}
