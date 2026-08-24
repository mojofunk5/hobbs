package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.Pilot;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.dto.PilotDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PilotMapperTest {

    private final Pilot pilot = new Pilot(PilotId.random(), "Alice", "alice@example.com");

    @Test
    void toPilotDtoCarriesAllFields() {
        OffsetDateTime signedUpAt = OffsetDateTime.now().minusDays(30);
        OffsetDateTime lastLoginAt = OffsetDateTime.now().minusHours(1);

        PilotDto dto = PilotMapper.toPilotDto(pilot, signedUpAt, lastLoginAt);

        assertThat(dto.getId(), is(pilot.getId().value()));
        assertThat(dto.getName(), is("Alice"));
        assertThat(dto.getEmail(), is("alice@example.com"));
        assertThat(dto.isDisabled(), is(false));
        assertThat(dto.getSignedUpAt(), is(signedUpAt));
        assertThat(dto.getLastLoginAt(), is(lastLoginAt));
    }

    @Test
    void toPilotDtoReflectsDisabledFlag() {
        Pilot disabledPilot = new Pilot(PilotId.random(), "Bob", "bob@example.com", true);

        PilotDto dto = PilotMapper.toPilotDto(disabledPilot, null, null);

        assertThat(dto.isDisabled(), is(true));
    }
}
