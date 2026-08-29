package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.Pilot;
import com.bonney.hobbs.dto.PilotDto;

import java.time.OffsetDateTime;

public class PilotMapper {

    private PilotMapper() {
        super();
    }

    public static PilotDto toPilotDto(Pilot domain, String email, Boolean disabled, OffsetDateTime signedUpAt, OffsetDateTime lastLoginAt) {
        return new PilotDto(domain.getId().value(), domain.getName(), email, disabled, signedUpAt, lastLoginAt);
    }
}
