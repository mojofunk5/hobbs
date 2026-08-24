package com.bonney.hobbs.mapper;

import com.bonney.hobbs.domain.ReferralCode;
import com.bonney.hobbs.dto.PendingInviteDto;

import java.time.OffsetDateTime;

public class ReferralCodeMapper {

    private ReferralCodeMapper() {
        super();
    }

    public static PendingInviteDto toPendingInviteDto(ReferralCode domain) {
        boolean expired = domain.getExpiresAt().isBefore(OffsetDateTime.now());
        return new PendingInviteDto(domain.getInvitedEmail(), domain.getCreatedAt(), domain.getExpiresAt(), expired);
    }
}
