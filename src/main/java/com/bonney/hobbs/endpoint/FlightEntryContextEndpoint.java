package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.SessionAuthFilter;
import com.bonney.hobbs.domain.Logbook;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.domain.Pilots;
import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.dto.AirfieldDto;
import com.bonney.hobbs.dto.FlightEntryContextDto;
import com.bonney.hobbs.dto.PilotSummaryDto;
import com.bonney.hobbs.mapper.AircraftMapper;
import com.bonney.hobbs.mapper.AirfieldMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.openapi.*;

import java.util.List;

public class FlightEntryContextEndpoint {

    private final Logbook logbook;
    private final Pilots pilots;

    public FlightEntryContextEndpoint(Logbook logbook, Pilots pilots) {
        this.logbook = logbook;
        this.pilots = pilots;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.get("flight-entry-context", this::flightEntryContext);
    }

    @OpenApi(
        path = "/flight-entry-context",
        methods = HttpMethod.GET,
        summary = "Prefetch everything the create-flight-entry screen's pickers need",
        description = "Aggregates GET /airfield/recent, GET /aircraft/recent, and GET /pilot?search= (no query) "
                + "into one response (see docs/plans/new-entry-context-endpoint.md) - aircraftId, "
                + "departureAirfieldId, arrivalAirfieldId, and pilotInCommandId are all required on FlightEntry, "
                + "so a pilot creating a new entry is essentially certain to focus every picker; one call up "
                + "front beats a round trip per picker focus on a poor connection. Takes no query params - "
                + "caller identity from the session is the only input. The three individual endpoints are "
                + "unchanged and stay - this is additive, not a replacement.",
        tags = {"FlightEntry"},
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlightEntryContextDto.class))
        }
    )
    private void flightEntryContext(Context context) {
        PilotId callerId = context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
        List<AirfieldDto> recentAirfields = logbook.recentAirfields(callerId).stream()
                .map(AirfieldMapper::toAirfieldDto)
                .toList();
        List<AircraftDto> recentAircraft = logbook.recentAircraft(callerId).stream()
                .map(AircraftMapper::toAircraftDto)
                .toList();
        List<PilotSummaryDto> knownPilots = pilots.searchKnownTo(callerId, null).stream()
                .map(p -> new PilotSummaryDto(p.getId().value(), p.getName()))
                .toList();
        context.json(new FlightEntryContextDto(recentAirfields, recentAircraft, knownPilots));
    }
}
