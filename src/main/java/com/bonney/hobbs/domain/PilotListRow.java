package com.bonney.hobbs.domain;

import java.time.OffsetDateTime;

public record PilotListRow(Pilot pilot, OffsetDateTime signedUpAt, OffsetDateTime lastLoginAt) {
}
