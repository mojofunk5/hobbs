package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Same field shape as FlightEntryDto minus the server-generated id. Used both for a fully
 * hand-filled entry and for the draft a GPS-recorded FlightTrack pre-fills - flightTrackId is
 * optional either way (see FlightEntry's Javadoc: GPS recording is a fast-path onto this same
 * form, never a requirement).
 */
public class CreateFlightEntryDto {

    private final UUID aircraftId;
    private final UUID flightTrackId;
    private final LocalDate date;
    private final String departurePlace;
    private final OffsetDateTime departureTime;
    private final String arrivalPlace;
    private final OffsetDateTime arrivalTime;
    private final UUID pilotInCommandId;
    private final UUID coPilotId;
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

    public CreateFlightEntryDto(@JsonProperty("aircraftId") UUID aircraftId,
                                 @JsonProperty("flightTrackId") UUID flightTrackId,
                                 @JsonProperty("date") LocalDate date,
                                 @JsonProperty("departurePlace") String departurePlace,
                                 @JsonProperty("departureTime") OffsetDateTime departureTime,
                                 @JsonProperty("arrivalPlace") String arrivalPlace,
                                 @JsonProperty("arrivalTime") OffsetDateTime arrivalTime,
                                 @JsonProperty("pilotInCommandId") UUID pilotInCommandId,
                                 @JsonProperty("coPilotId") UUID coPilotId,
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
        this.aircraftId = aircraftId;
        this.flightTrackId = flightTrackId;
        this.date = date;
        this.departurePlace = departurePlace;
        this.departureTime = departureTime;
        this.arrivalPlace = arrivalPlace;
        this.arrivalTime = arrivalTime;
        this.pilotInCommandId = pilotInCommandId;
        this.coPilotId = coPilotId;
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

    public UUID getAircraftId() {
        return aircraftId;
    }

    public UUID getFlightTrackId() {
        return flightTrackId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDeparturePlace() {
        return departurePlace;
    }

    public OffsetDateTime getDepartureTime() {
        return departureTime;
    }

    public String getArrivalPlace() {
        return arrivalPlace;
    }

    public OffsetDateTime getArrivalTime() {
        return arrivalTime;
    }

    public UUID getPilotInCommandId() {
        return pilotInCommandId;
    }

    public UUID getCoPilotId() {
        return coPilotId;
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
