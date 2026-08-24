package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.Aircraft;
import com.bonney.hobbs.dto.AircraftDto;

public class AircraftMapper {

    private AircraftMapper() {
        super();
    }

    public static AircraftDto toAircraftDto(Aircraft domain) {
        return new AircraftDto(domain.getId().value(), domain.getRegistration(), domain.getMake(),
                domain.getModel(), domain.getEngineCategory().name());
    }
}
