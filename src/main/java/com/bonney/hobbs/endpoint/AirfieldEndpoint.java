package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.SessionAuthFilter;
import com.bonney.hobbs.domain.Logbook;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.dto.AirfieldDto;
import com.bonney.hobbs.mapper.AirfieldMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.openapi.*;

import java.util.List;

public class AirfieldEndpoint {

    private final Logbook logbook;

    public AirfieldEndpoint(Logbook logbook) {
        this.logbook = logbook;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.get("airfield", this::searchAirfields);
    }

    @OpenApi(
        path = "/airfield",
        methods = HttpMethod.GET,
        summary = "Search airfield reference data",
        description = "Searches airfields seeded from OurAirports' GB dataset (see "
                + "docs/plans/airfield-picker.md) - airfields are reference data, not pilot-submitted; there is "
                + "no POST /airfield. `search` matches a case-insensitive substring of name OR an exact/prefix "
                + "match of icaoCode, combined in one query. Unlike GET /aircraft?search=, `search` is optional - "
                + "against the ~1,200-row GB-only imported set, an empty or missing search reasonably returns "
                + "everything. Results are ordered with the calling pilot's own last 5 distinct recently-flown "
                + "airfields first (most recent first, deduped), everything else alphabetical by name after - "
                + "applies whether search is empty or not.",
        tags = {"Airfield"},
        queryParams = @OpenApiParam(name = "search", type = String.class,
                description = "Case-insensitive substring match on name, or exact/prefix match on icaoCode"),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = AirfieldDto[].class))
        }
    )
    private void searchAirfields(Context context) {
        PilotId callerId = context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
        String search = context.queryParam("search");
        List<AirfieldDto> airfields = logbook.searchAirfields(callerId, search).stream()
                .map(AirfieldMapper::toAirfieldDto)
                .toList();
        context.json(airfields);
    }
}
