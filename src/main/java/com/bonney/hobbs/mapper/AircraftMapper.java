package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.Aircraft;
import com.bonney.hobbs.dto.AircraftDto;

public class AircraftMapper {

    private AircraftMapper() {
        super();
    }

    public static AircraftDto toAircraftDto(Aircraft domain) {
        String engineCategory = domain.getEngineCategory() == null ? null : domain.getEngineCategory().name();
        return new AircraftDto(domain.getId().value(), domain.getRegistration(), domain.getMake(), domain.getModel(),
                engineCategory, domain.getManufacturerIcao(), domain.getTypeCode(), domain.getSerialNumber(),
                domain.getOperator(), domain.getOwner(), domain.getBuilt(), domain.getEngines(),
                domain.getCategoryDescription());
    }
}
