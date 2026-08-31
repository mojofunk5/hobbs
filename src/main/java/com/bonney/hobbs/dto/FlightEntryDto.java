package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@OpenApiName("FlightEntry")
public class FlightEntryDto {

    private final UUID id;
    private final UUID aircraftId;
    private final UUID flightTrackId;
    private final LocalDate date;
    private final OffsetDateTime departureTime;
    private final OffsetDateTime arrivalTime;
    private final UUID departureAirfieldId;
    private final UUID arrivalAirfieldId;
    private final UUID pilotInCommandId;
    private final UUID coPilotId;
    private final String holderOperatingCapacity;
    private final String holderOperatingCapacityNotation;
    private final int singleEngineMinutes;
    private final int multiEngineMinutes;
    private final int totalMinutes;
    private final int nightMinutes;
    private final int ifrMinutes;
    private final int crossCountryMinutes;
    private final int pilotInCommandMinutes;
    private final int coPilotMinutes;
    private final int dualMinutes;
    private final int instructorMinutes;
    private final int dayLandings;
    private final int nightLandings;
    private final String remarks;

    public FlightEntryDto(@JsonProperty("id") UUID id, @JsonProperty("aircraftId") UUID aircraftId,
                           @JsonProperty("flightTrackId") UUID flightTrackId, @JsonProperty("date") LocalDate date,
                           @JsonProperty("departureTime") OffsetDateTime departureTime,
                           @JsonProperty("arrivalTime") OffsetDateTime arrivalTime,
                           @JsonProperty("departureAirfieldId") UUID departureAirfieldId,
                           @JsonProperty("arrivalAirfieldId") UUID arrivalAirfieldId,
                           @JsonProperty("pilotInCommandId") UUID pilotInCommandId,
                           @JsonProperty("coPilotId") UUID coPilotId,
                           @JsonProperty("holderOperatingCapacity") String holderOperatingCapacity,
                           @JsonProperty("holderOperatingCapacityNotation") String holderOperatingCapacityNotation,
                           @JsonProperty("singleEngineMinutes") int singleEngineMinutes,
                           @JsonProperty("multiEngineMinutes") int multiEngineMinutes,
                           @JsonProperty("totalMinutes") int totalMinutes,
                           @JsonProperty("nightMinutes") int nightMinutes,
                           @JsonProperty("ifrMinutes") int ifrMinutes,
                           @JsonProperty("crossCountryMinutes") int crossCountryMinutes,
                           @JsonProperty("pilotInCommandMinutes") int pilotInCommandMinutes,
                           @JsonProperty("coPilotMinutes") int coPilotMinutes,
                           @JsonProperty("dualMinutes") int dualMinutes,
                           @JsonProperty("instructorMinutes") int instructorMinutes,
                           @JsonProperty("dayLandings") int dayLandings,
                           @JsonProperty("nightLandings") int nightLandings,
                           @JsonProperty("remarks") String remarks) {
        this.id = id;
        this.aircraftId = aircraftId;
        this.flightTrackId = flightTrackId;
        this.date = date;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.departureAirfieldId = departureAirfieldId;
        this.arrivalAirfieldId = arrivalAirfieldId;
        this.pilotInCommandId = pilotInCommandId;
        this.coPilotId = coPilotId;
        this.holderOperatingCapacity = holderOperatingCapacity;
        this.holderOperatingCapacityNotation = holderOperatingCapacityNotation;
        this.singleEngineMinutes = singleEngineMinutes;
        this.multiEngineMinutes = multiEngineMinutes;
        this.totalMinutes = totalMinutes;
        this.nightMinutes = nightMinutes;
        this.ifrMinutes = ifrMinutes;
        this.crossCountryMinutes = crossCountryMinutes;
        this.pilotInCommandMinutes = pilotInCommandMinutes;
        this.coPilotMinutes = coPilotMinutes;
        this.dualMinutes = dualMinutes;
        this.instructorMinutes = instructorMinutes;
        this.dayLandings = dayLandings;
        this.nightLandings = nightLandings;
        this.remarks = remarks;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAircraftId() {
        return aircraftId;
    }

    public UUID getFlightTrackId() {
        return flightTrackId;
    }

    public LocalDate getDate() {
        return date;
    }

    public OffsetDateTime getDepartureTime() {
        return departureTime;
    }

    public OffsetDateTime getArrivalTime() {
        return arrivalTime;
    }

    public UUID getDepartureAirfieldId() {
        return departureAirfieldId;
    }

    public UUID getArrivalAirfieldId() {
        return arrivalAirfieldId;
    }

    public UUID getPilotInCommandId() {
        return pilotInCommandId;
    }

    public UUID getCoPilotId() {
        return coPilotId;
    }

    public String getHolderOperatingCapacity() {
        return holderOperatingCapacity;
    }

    public String getHolderOperatingCapacityNotation() {
        return holderOperatingCapacityNotation;
    }

    public int getSingleEngineMinutes() {
        return singleEngineMinutes;
    }

    public int getMultiEngineMinutes() {
        return multiEngineMinutes;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public int getNightMinutes() {
        return nightMinutes;
    }

    public int getIfrMinutes() {
        return ifrMinutes;
    }

    public int getCrossCountryMinutes() {
        return crossCountryMinutes;
    }

    public int getPilotInCommandMinutes() {
        return pilotInCommandMinutes;
    }

    public int getCoPilotMinutes() {
        return coPilotMinutes;
    }

    public int getDualMinutes() {
        return dualMinutes;
    }

    public int getInstructorMinutes() {
        return instructorMinutes;
    }

    public int getDayLandings() {
        return dayLandings;
    }

    public int getNightLandings() {
        return nightLandings;
    }

    public String getRemarks() {
        return remarks;
    }
}
