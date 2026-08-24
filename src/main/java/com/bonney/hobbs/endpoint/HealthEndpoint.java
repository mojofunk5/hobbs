package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.dto.HealthDto;
import com.bonney.hobbs.dto.VersionDto;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.openapi.*;

public class HealthEndpoint {

    public void registerRoutes(JavalinConfig config) {
        config.routes.get("health", this::health);
        config.routes.get("version", this::version);
    }

    @OpenApi(
        path = "/health",
        methods = HttpMethod.GET,
        summary = "Health check",
        description = "Always returns UP if the server is running and able to respond. No auth required.",
        tags = {"System"},
        responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = HealthDto.class))
    )
    private void health(Context context) {
        context.json(new HealthDto("UP"));
    }

    @OpenApi(
        path = "/version",
        methods = HttpMethod.GET,
        summary = "Running build version",
        description = "Returns the git SHA baked into the Docker image at build time (Dockerfile's GIT_SHA build "
                + "arg, set from github.sha in CI) - travels with the image regardless of deploy mechanics, rather "
                + "than depending on a runtime env var being wired through correctly. Falls back to \"unknown\" "
                + "outside Docker (e.g. local ./gradlew runs). No auth required.",
        tags = {"System"},
        responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = VersionDto.class))
    )
    private void version(Context context) {
        context.json(new VersionDto(System.getenv().getOrDefault("GIT_SHA", "unknown")));
    }
}
