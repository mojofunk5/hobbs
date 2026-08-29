package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.Pilot;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.dto.PilotDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class PilotMapperTest {

    private final Pilot pilot = new Pilot(PilotId.random(), "Alice", null);

    @Test
    void toPilotDtoCarriesAllFields() {
        OffsetDateTime signedUpAt = OffsetDateTime.now().minusDays(30);
        OffsetDateTime lastLoginAt = OffsetDateTime.now().minusHours(1);

        PilotDto dto = PilotMapper.toPilotDto(pilot, "alice@example.com", false, signedUpAt, lastLoginAt);

        assertThat(dto.getId(), is(pilot.getId().value()));
        assertThat(dto.getName(), is("Alice"));
        assertThat(dto.getEmail(), is("alice@example.com"));
        assertThat(dto.isDisabled(), is(false));
        assertThat(dto.getSignedUpAt(), is(signedUpAt));
        assertThat(dto.getLastLoginAt(), is(lastLoginAt));
    }

    @Test
    void toPilotDtoReflectsDisabledFlag() {
        PilotDto dto = PilotMapper.toPilotDto(pilot, "bob@example.com", true, null, null);

        assertThat(dto.isDisabled(), is(true));
    }

    @Test
    void toPilotDtoLeavesEmailAndDisabledNullForAnUnclaimedPilot() {
        PilotDto dto = PilotMapper.toPilotDto(pilot, null, null, null, null);

        assertThat(dto.getEmail(), is(nullValue()));
        assertThat(dto.isDisabled(), is(nullValue()));
    }
}
