package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.SessionAuthFilter;
import com.bonney.hobbs.domain.AircraftId;
import com.bonney.hobbs.domain.AirfieldId;
import com.bonney.hobbs.domain.FlightEntry;
import com.bonney.hobbs.domain.FlightEntryId;
import com.bonney.hobbs.domain.FlightTrackId;
import com.bonney.hobbs.domain.Logbook;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.dto.CreateFlightEntryDto;
import com.bonney.hobbs.dto.FlightEntryDto;
import com.bonney.hobbs.mapper.FlightEntryMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.*;

import java.util.List;
import java.util.UUID;

public class FlightEntryEndpoint {

    private final Logbook logbook;

    public FlightEntryEndpoint(Logbook logbook) {
        this.logbook = logbook;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.post("flight", this::createFlightEntry);
        config.routes.get("flight", this::listFlightEntries);
        config.routes.get("flight/{flightEntryId}", this::getFlightEntry);
    }

    @OpenApi(
        path = "/flight",
        methods = HttpMethod.POST,
        summary = "Create a flight entry",
        description = "Creates a new logbook entry for the authenticated pilot. flightTrackId is optional - "
                + "GPS recording is a fast-path onto this same form, never a requirement; a manually-entered "
                + "flight is just as valid as one derived from a recorded track. departureAirfieldId/"
                + "arrivalAirfieldId are required, referencing the self-owned airfield reference table (see "
                + "docs/plans/airfield-picker.md) - there is no free-text departurePlace/arrivalPlace fallback "
                + "any more. holderOperatingCapacity is required - the CAP804 role the logbook's owner played "
                + "on this flight (see docs/plans/holder-operating-capacity.md); the response also carries its "
                + "human-readable notation, derived server-side.",
        tags = {"FlightEntry"},
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateFlightEntryDto.class)),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlightEntryDto.class))
        }
    )
    private void createFlightEntry(Context context) {
        PilotId pilotId = authenticatedPilotId(context);
        CreateFlightEntryDto request = context.bodyAsClass(CreateFlightEntryDto.class);
        FlightEntry entry = logbook.createEntry(
                pilotId,
                AircraftId.from(request.getAircraftId()),
                request.getFlightTrackId() == null ? null : FlightTrackId.from(request.getFlightTrackId()),
                request.getDate(),
                request.getDepartureTime(),
                request.getArrivalTime(),
                AirfieldId.from(request.getDepartureAirfieldId()),
                AirfieldId.from(request.getArrivalAirfieldId()),
                PilotId.from(request.getPilotInCommandId()),
                request.getCoPilotId() == null ? null : PilotId.from(request.getCoPilotId()),
                request.getHolderOperatingCapacity(),
                request.getSingleEngineMinutes(),
                request.getMultiEngineMinutes(),
                request.getTotalMinutes(),
                request.getNightMinutes(),
                request.getIfrMinutes(),
                request.getCrossCountryMinutes(),
                request.getPilotInCommandMinutes(),
                request.getCoPilotMinutes(),
                request.getDualMinutes(),
                request.getInstructorMinutes(),
                request.getDayLandings(),
                request.getNightLandings(),
                request.getRemarks());
        context.json(FlightEntryMapper.toFlightEntryDto(entry));
    }

    @OpenApi(
        path = "/flight",
        methods = HttpMethod.GET,
        summary = "List the authenticated pilot's flight entries",
        tags = {"FlightEntry"},
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlightEntryDto[].class))
        }
    )
    private void listFlightEntries(Context context) {
        PilotId pilotId = authenticatedPilotId(context);
        List<FlightEntryDto> entries = logbook.listForPilot(pilotId).stream()
                .map(FlightEntryMapper::toFlightEntryDto)
                .toList();
        context.json(entries);
    }

    @OpenApi(
        path = "/flight/{flightEntryId}",
        methods = HttpMethod.GET,
        summary = "Get a flight entry",
        tags = {"FlightEntry"},
        pathParams = @OpenApiParam(name = "flightEntryId", type = UUID.class, required = true),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = FlightEntryDto.class)),
            @OpenApiResponse(status = "403", description = "Not the entry's pilot"),
            @OpenApiResponse(status = "404")
        }
    )
    private void getFlightEntry(Context context) {
        PilotId pilotId = authenticatedPilotId(context);
        FlightEntryId flightEntryId = FlightEntryId.from(context.pathParamAsClass("flightEntryId", UUID.class).get());
        FlightEntry entry = logbook.get(flightEntryId).orElseThrow();
        if (!entry.getPilotId().equals(pilotId)) {
            context.status(HttpStatus.FORBIDDEN);
            return;
        }
        context.json(FlightEntryMapper.toFlightEntryDto(entry));
    }

    private PilotId authenticatedPilotId(Context context) {
        return context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
    }
}
