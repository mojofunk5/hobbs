package com.bonney.hobbs.domain;

import java.time.OffsetDateTime;

/**
 * A row from the admin pilot list - {@code email}/{@code disabled} are {@code null} for a pilot
 * with no {@link Account} (e.g. an unclaimed record created as someone else's co-pilot).
 */
public record PilotListRow(Pilot pilot, String email, Boolean disabled, OffsetDateTime signedUpAt, OffsetDateTime lastLoginAt) {
}
