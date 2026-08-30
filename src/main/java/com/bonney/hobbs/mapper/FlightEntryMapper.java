package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.FlightEntry;
import com.bonney.hobbs.dto.FlightEntryDto;

public class FlightEntryMapper {

    private FlightEntryMapper() {
        super();
    }

    public static FlightEntryDto toFlightEntryDto(FlightEntry domain) {
        return new FlightEntryDto(
                domain.getId().value(),
                domain.getAircraftId().value(),
                domain.getFlightTrackId().map(id -> id.value()).orElse(null),
                domain.getDate(),
                domain.getDeparturePlace(),
                domain.getDepartureTime(),
                domain.getArrivalPlace(),
                domain.getArrivalTime(),
                domain.getDepartureAirfieldId().map(id -> id.value()).orElse(null),
                domain.getArrivalAirfieldId().map(id -> id.value()).orElse(null),
                domain.getPilotInCommandId().value(),
                domain.getCoPilotId().map(id -> id.value()).orElse(null),
                domain.getSingleEngineMinutes(),
                domain.getMultiEngineMinutes(),
                domain.getTotalMinutes(),
                domain.getNightMinutes(),
                domain.getIfrMinutes(),
                domain.getCrossCountryMinutes(),
                domain.getPilotInCommandMinutes(),
                domain.getCoPilotMinutes(),
                domain.getDualMinutes(),
                domain.getInstructorMinutes(),
                domain.getDayLandings(),
                domain.getNightLandings(),
                domain.getRemarks());
    }
}
