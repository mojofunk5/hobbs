package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.domain.Logbook;
import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.mapper.AircraftMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.openapi.*;

import java.util.List;

public class AircraftEndpoint {

    private final Logbook logbook;

    public AircraftEndpoint(Logbook logbook) {
        this.logbook = logbook;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.get("aircraft", this::searchAircraft);
    }

    @OpenApi(
        path = "/aircraft",
        methods = HttpMethod.GET,
        summary = "Search aircraft reference data",
        description = "Searches aircraft seeded from OpenSky's aircraftDatabase.csv (see "
                + "docs/plans/aircraft-picker.md) - aircraft are reference data, not pilot-submitted; there is "
                + "no POST /aircraft. Unlike GET /pilot?search=, `search` is required (minimum 2 characters) - "
                + "against the full imported dataset (~600k rows globally, not just UK-registered), an empty or "
                + "missing search would mean \"everything\", not a sane response. Case-insensitive substring match, "
                + "capped at 50 results ordered by registration. By default matches across "
                + "registration/make/model (the Browse Aircraft page's shape); `registrationOnly=true` narrows "
                + "this to registration alone, for the flight-entry aircraft picker - a pilot who already knows "
                + "the tail number doesn't want incidental hits on a make/model substring (e.g. searching "
                + "\"warrior\" would otherwise surface every Piper Warrior in the dataset).",
        tags = {"Aircraft"},
        queryParams = {
            @OpenApiParam(name = "search", type = String.class, required = true,
                    description = "Case-insensitive substring match - required, minimum 2 characters"),
            @OpenApiParam(name = "registrationOnly", type = Boolean.class,
                    description = "When true, match registration only (not make/model) - defaults to false")
        },
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = AircraftDto[].class)),
            @OpenApiResponse(status = "400", description = "search is missing or shorter than 2 characters")
        }
    )
    private void searchAircraft(Context context) {
        String search = context.queryParam("search");
        boolean registrationOnly = context.queryParamAsClass("registrationOnly", Boolean.class).getOrDefault(false);
        List<AircraftDto> aircraft = logbook.searchAircraft(search, registrationOnly).stream()
                .map(AircraftMapper::toAircraftDto)
                .toList();
        context.json(aircraft);
    }
}
