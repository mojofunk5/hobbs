package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.domain.Aircraft;
import com.bonney.hobbs.domain.EngineCategory;
import com.bonney.hobbs.domain.Logbook;
import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.dto.CreateAircraftDto;
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
        config.routes.post("aircraft", this::createAircraft);
        config.routes.get("aircraft", this::listAircraft);
    }

    @OpenApi(
        path = "/aircraft",
        methods = HttpMethod.POST,
        summary = "Register an aircraft",
        description = "Registers an aircraft (registration, make, model, engine category) so it can be "
                + "referenced from flight entries. Shared across all pilots, not scoped to one account - "
                + "an aircraft like a shared club trainer only needs registering once.",
        tags = {"Aircraft"},
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateAircraftDto.class)),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = AircraftDto.class))
        }
    )
    private void createAircraft(Context context) {
        CreateAircraftDto request = context.bodyAsClass(CreateAircraftDto.class);
        Aircraft aircraft = logbook.createAircraft(request.getRegistration(), request.getMake(),
                request.getModel(), EngineCategory.valueOf(request.getEngineCategory()));
        context.json(AircraftMapper.toAircraftDto(aircraft));
    }

    @OpenApi(
        path = "/aircraft",
        methods = HttpMethod.GET,
        summary = "List all registered aircraft",
        tags = {"Aircraft"},
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = AircraftDto[].class))
        }
    )
    private void listAircraft(Context context) {
        List<AircraftDto> aircraft = logbook.listAircraft().stream()
                .map(AircraftMapper::toAircraftDto)
                .toList();
        context.json(aircraft);
    }
}
