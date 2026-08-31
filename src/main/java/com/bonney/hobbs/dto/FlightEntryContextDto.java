package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.util.List;

/**
 * Backs GET /flight-entry-context - see docs/plans/new-entry-context-endpoint.md. Aggregates the
 * same data and shapes as GET /airfield/recent, GET /aircraft/recent, and GET /pilot?search= (no
 * query) into one response, for the create-flight-entry screen's four required pickers.
 */
@OpenApiName("FlightEntryContext")
public class FlightEntryContextDto {

    private final List<AirfieldDto> recentAirfields;
    private final List<AircraftDto> recentAircraft;
    private final List<PilotSummaryDto> knownPilots;

    public FlightEntryContextDto(@JsonProperty("recentAirfields") List<AirfieldDto> recentAirfields,
                                  @JsonProperty("recentAircraft") List<AircraftDto> recentAircraft,
                                  @JsonProperty("knownPilots") List<PilotSummaryDto> knownPilots) {
        this.recentAirfields = recentAirfields;
        this.recentAircraft = recentAircraft;
        this.knownPilots = knownPilots;
    }

    public List<AirfieldDto> getRecentAirfields() {
        return recentAirfields;
    }

    public List<AircraftDto> getRecentAircraft() {
        return recentAircraft;
    }

    public List<PilotSummaryDto> getKnownPilots() {
        return knownPilots;
    }
}
