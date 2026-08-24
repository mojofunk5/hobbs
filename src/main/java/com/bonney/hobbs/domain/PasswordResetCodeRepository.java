package com.bonney.hobbs.domain;

import org.jooq.DSLContext;

import java.time.OffsetDateTime;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.PASSWORD_RESET_CODE;

public class PasswordResetCodeRepository {

    private final DSLContext dsl;

    public PasswordResetCodeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(PasswordResetCode resetCode) {
        dsl.insertInto(PASSWORD_RESET_CODE)
                .set(PASSWORD_RESET_CODE.ID, resetCode.getId().value())
                .set(PASSWORD_RESET_CODE.PILOT_ID, resetCode.getPilotId().value())
                .set(PASSWORD_RESET_CODE.CODE, resetCode.getCode())
                .set(PASSWORD_RESET_CODE.CREATED_AT, resetCode.getCreatedAt())
                .set(PASSWORD_RESET_CODE.EXPIRES_AT, resetCode.getExpiresAt())
                .execute();
    }

    // Scoped to (pilotId, code) rather than code alone - a 6-digit code is too small a keyspace to
    // safely treat as globally unique (unlike ReferralCode's UUID-based code), but the confirm
    // endpoint always knows the pilot via the email in the request body, so this loses nothing.
    public Optional<PasswordResetCode> findUnusedByPilotIdAndCode(PilotId pilotId, String code) {
        return dsl.selectFrom(PASSWORD_RESET_CODE)
                .where(PASSWORD_RESET_CODE.PILOT_ID.eq(pilotId.value()))
                .and(PASSWORD_RESET_CODE.CODE.eq(code))
                .and(PASSWORD_RESET_CODE.USED_AT.isNull())
                .and(PASSWORD_RESET_CODE.EXPIRES_AT.gt(OffsetDateTime.now()))
                .fetchOptional()
                .map(r -> new PasswordResetCode(
                        PasswordResetCodeId.from(r.get(PASSWORD_RESET_CODE.ID)),
                        PilotId.from(r.get(PASSWORD_RESET_CODE.PILOT_ID)),
                        r.get(PASSWORD_RESET_CODE.CODE),
                        r.get(PASSWORD_RESET_CODE.CREATED_AT),
                        r.get(PASSWORD_RESET_CODE.EXPIRES_AT)));
    }

    public void markUsed(PasswordResetCodeId id) {
        dsl.update(PASSWORD_RESET_CODE)
                .set(PASSWORD_RESET_CODE.USED_AT, OffsetDateTime.now())
                .where(PASSWORD_RESET_CODE.ID.eq(id.value()))
                .execute();
    }

    // A new reset request supersedes any still-pending one for the same pilot - same pattern as
    // ReferralCodeRepository.expireUnusedForEmail, so only the most recently issued code is ever valid.
    public void invalidateUnusedForPilot(PilotId pilotId) {
        dsl.update(PASSWORD_RESET_CODE)
                .set(PASSWORD_RESET_CODE.EXPIRES_AT, OffsetDateTime.now())
                .where(PASSWORD_RESET_CODE.PILOT_ID.eq(pilotId.value()))
                .and(PASSWORD_RESET_CODE.USED_AT.isNull())
                .execute();
    }
}
