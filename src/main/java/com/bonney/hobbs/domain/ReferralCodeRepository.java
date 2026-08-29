package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.Tables;
import org.jooq.DSLContext;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReferralCodeRepository {

    private final DSLContext dsl;

    public ReferralCodeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(ReferralCode code) {
        dsl.insertInto(Tables.REFERRAL_CODE)
                .set(Tables.REFERRAL_CODE.CODE, code.getCode())
                .set(Tables.REFERRAL_CODE.CREATED_BY, code.getCreatedBy().value())
                .set(Tables.REFERRAL_CODE.CREATED_AT, code.getCreatedAt())
                .set(Tables.REFERRAL_CODE.INVITED_EMAIL, code.getInvitedEmail())
                .set(Tables.REFERRAL_CODE.EXPIRES_AT, code.getExpiresAt())
                .set(Tables.REFERRAL_CODE.CLAIMS_PILOT_ID, code.getClaimsPilotId() == null ? null : code.getClaimsPilotId().value())
                .execute();
    }

    public Optional<ReferralCode> findUnusedByCode(String code) {
        return dsl.selectFrom(Tables.REFERRAL_CODE)
                .where(Tables.REFERRAL_CODE.CODE.eq(code))
                .and(Tables.REFERRAL_CODE.USED_AT.isNull())
                .and(Tables.REFERRAL_CODE.CANCELLED_AT.isNull())
                .and(Tables.REFERRAL_CODE.EXPIRES_AT.gt(OffsetDateTime.now()))
                .fetchOptional()
                .map(r -> new ReferralCode(r.getCode(), PilotId.from(r.getCreatedBy()), r.getCreatedAt(), r.getInvitedEmail(), r.getExpiresAt(),
                        r.getClaimsPilotId() == null ? null : PilotId.from(r.getClaimsPilotId())));
    }

    // Collapsed to one row per email (the most recent) - an admin re-inviting the same address
    // shouldn't leave old rows cluttering the list. The query is already sorted newest-first, so
    // keeping only the first occurrence per email in a LinkedHashMap does this without a window
    // function, and preserves overall ordering.
    public List<ReferralCode> listUnused() {
        Map<String, ReferralCode> latestByEmail = new LinkedHashMap<>();
        dsl.selectFrom(Tables.REFERRAL_CODE)
                .where(Tables.REFERRAL_CODE.USED_AT.isNull())
                .and(Tables.REFERRAL_CODE.CANCELLED_AT.isNull())
                .orderBy(Tables.REFERRAL_CODE.CREATED_AT.desc())
                .fetch()
                .forEach(r -> latestByEmail.putIfAbsent(r.getInvitedEmail(),
                        new ReferralCode(r.getCode(), PilotId.from(r.getCreatedBy()), r.getCreatedAt(), r.getInvitedEmail(), r.getExpiresAt(),
                                r.getClaimsPilotId() == null ? null : PilotId.from(r.getClaimsPilotId()))));
        return List.copyOf(latestByEmail.values());
    }

    public void markUsed(String code, PilotId usedBy) {
        dsl.update(Tables.REFERRAL_CODE)
                .set(Tables.REFERRAL_CODE.USED_BY, usedBy.value())
                .set(Tables.REFERRAL_CODE.USED_AT, OffsetDateTime.now())
                .where(Tables.REFERRAL_CODE.CODE.eq(code))
                .execute();
    }

    // Re-inviting is the only renewal mechanism - no separate extend/renew endpoint - so only the
    // most recently issued code for an email should ever be valid. Also sets cancelled_at (not just
    // expires_at) so the superseded row is fully retired: listUnused()'s per-email collapse normally
    // hides it behind the fresh replacement code anyway, but if that replacement is later used,
    // markUsed() drops it out of listUnused()'s result set - and without cancelled_at here, this old
    // row would then resurface as the only remaining row for that email, showing a re-invited-and-
    // since-registered pilot as a stale "Expired" pending invite.
    public void expireUnusedForEmail(String email) {
        dsl.update(Tables.REFERRAL_CODE)
                .set(Tables.REFERRAL_CODE.EXPIRES_AT, OffsetDateTime.now())
                .set(Tables.REFERRAL_CODE.CANCELLED_AT, OffsetDateTime.now())
                .where(Tables.REFERRAL_CODE.INVITED_EMAIL.eq(email))
                .and(Tables.REFERRAL_CODE.USED_AT.isNull())
                .execute();
    }

    // An admin-initiated "delete" - soft, like everything else in this domain (disabled_at,
    // deleted_at, used_at), but unlike expiry it must also hide the row from listUnused() entirely
    // rather than showing it with a status, since the point is for the admin to make it go away.
    public void cancelUnusedForEmail(String email) {
        dsl.update(Tables.REFERRAL_CODE)
                .set(Tables.REFERRAL_CODE.CANCELLED_AT, OffsetDateTime.now())
                .where(Tables.REFERRAL_CODE.INVITED_EMAIL.eq(email))
                .and(Tables.REFERRAL_CODE.USED_AT.isNull())
                .execute();
    }
}
