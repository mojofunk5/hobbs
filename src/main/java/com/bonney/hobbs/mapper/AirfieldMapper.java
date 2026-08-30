package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.Airfield;
import com.bonney.hobbs.dto.AirfieldDto;

public class AirfieldMapper {

    private AirfieldMapper() {
        super();
    }

    public static AirfieldDto toAirfieldDto(Airfield domain) {
        return new AirfieldDto(domain.getId().value(), domain.getIcaoCode(), domain.getName(),
                domain.getMunicipality(), domain.getIsoCountry(), domain.getIsoRegion(), domain.getLatitude(),
                domain.getLongitude(), domain.getElevationFt(), domain.getType());
    }
}
