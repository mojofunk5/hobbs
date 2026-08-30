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
                + "missing search would mean \"everything\", not a sane response. Case-insensitive substring match "
                + "across registration/make/model, capped at 50 results ordered by registration. Backs both the "
                + "flight-entry aircraft picker and the Browse Aircraft page.",
        tags = {"Aircraft"},
        queryParams = @OpenApiParam(name = "search", type = String.class, required = true,
                description = "Case-insensitive substring match on registration/make/model - required, minimum 2 characters"),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = AircraftDto[].class)),
            @OpenApiResponse(status = "400", description = "search is missing or shorter than 2 characters")
        }
    )
    private void searchAircraft(Context context) {
        String search = context.queryParam("search");
        List<AircraftDto> aircraft = logbook.searchAircraft(search).stream()
                .map(AircraftMapper::toAircraftDto)
                .toList();
        context.json(aircraft);
    }
}
