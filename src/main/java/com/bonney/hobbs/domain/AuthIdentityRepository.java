package com.bonney.hobbs.domain;

import org.jooq.DSLContext;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.AUTH_IDENTITY;

public class AuthIdentityRepository {

    private final DSLContext dsl;

    public AuthIdentityRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(AuthIdentity identity) {
        dsl.insertInto(AUTH_IDENTITY)
                .set(AUTH_IDENTITY.ID, identity.getId().value())
                .set(AUTH_IDENTITY.PILOT_ID, identity.getPilotId().value())
                .set(AUTH_IDENTITY.TYPE, identity.getType().name())
                .set(AUTH_IDENTITY.IDENTIFIER, identity.getIdentifier())
                .set(AUTH_IDENTITY.HASHED_CREDENTIAL, identity.getHashedCredential())
                .set(AUTH_IDENTITY.CREATED_AT, OffsetDateTime.now())
                .execute();
    }

    public void updateHashedCredential(PilotId pilotId, AuthIdentityType type, String hashedCredential) {
        dsl.update(AUTH_IDENTITY)
                .set(AUTH_IDENTITY.HASHED_CREDENTIAL, hashedCredential)
                .where(AUTH_IDENTITY.PILOT_ID.eq(pilotId.value()))
                .and(AUTH_IDENTITY.TYPE.eq(type.name()))
                .execute();
    }

    // Touched on every session-creating event (login, register, and a successful password reset -
    // all three authenticate the pilot), not just POST /auth/login, so this reads as "last active"
    // rather than strictly "last call to the login endpoint".
    public void touchLastLogin(PilotId pilotId, AuthIdentityType type) {
        dsl.update(AUTH_IDENTITY)
                .set(AUTH_IDENTITY.LAST_LOGIN_AT, OffsetDateTime.now())
                .where(AUTH_IDENTITY.PILOT_ID.eq(pilotId.value()))
                .and(AUTH_IDENTITY.TYPE.eq(type.name()))
                .execute();
    }

    public Optional<AuthIdentity> findByTypeAndIdentifier(AuthIdentityType type, String identifier) {
        return Optional.ofNullable(
                dsl.selectFrom(AUTH_IDENTITY)
                        .where(AUTH_IDENTITY.TYPE.eq(type.name()))
                        .and(AUTH_IDENTITY.IDENTIFIER.eq(identifier))
                        .fetchOne())
                .map(r -> new AuthIdentity(
                        AuthIdentityId.from(r.get(AUTH_IDENTITY.ID)),
                        PilotId.from(r.get(AUTH_IDENTITY.PILOT_ID)),
                        AuthIdentityType.valueOf(r.get(AUTH_IDENTITY.TYPE)),
                        r.get(AUTH_IDENTITY.IDENTIFIER),
                        r.get(AUTH_IDENTITY.HASHED_CREDENTIAL)));
    }

    // Every pilot has exactly one PASSWORD identity created in the same transaction as
    // registration, so this doubles as "when did this pilot sign up" without needing a created_at
    // on pilot itself.
    public Map<PilotId, OffsetDateTime> findCreatedAtByPilotIds(Collection<PilotId> pilotIds) {
        return dsl.select(AUTH_IDENTITY.PILOT_ID, AUTH_IDENTITY.CREATED_AT)
                .from(AUTH_IDENTITY)
                .where(AUTH_IDENTITY.TYPE.eq(AuthIdentityType.PASSWORD.name()))
                .and(AUTH_IDENTITY.PILOT_ID.in(pilotIds.stream().map(PilotId::value).toList()))
                .fetchMap(r -> PilotId.from(r.get(AUTH_IDENTITY.PILOT_ID)), r -> r.get(AUTH_IDENTITY.CREATED_AT));
    }

    // Rows that have never had a login/register/reset event since last_login_at was added have a
    // NULL here and are simply omitted from the returned map - callers already handle a missing
    // entry (see PilotMapper), same as findCreatedAtByPilotIds.
    public Map<PilotId, OffsetDateTime> findLastLoginAtByPilotIds(Collection<PilotId> pilotIds) {
        return dsl.select(AUTH_IDENTITY.PILOT_ID, AUTH_IDENTITY.LAST_LOGIN_AT)
                .from(AUTH_IDENTITY)
                .where(AUTH_IDENTITY.TYPE.eq(AuthIdentityType.PASSWORD.name()))
                .and(AUTH_IDENTITY.PILOT_ID.in(pilotIds.stream().map(PilotId::value).toList()))
                .and(AUTH_IDENTITY.LAST_LOGIN_AT.isNotNull())
                .fetchMap(r -> PilotId.from(r.get(AUTH_IDENTITY.PILOT_ID)), r -> r.get(AUTH_IDENTITY.LAST_LOGIN_AT));
    }
}
