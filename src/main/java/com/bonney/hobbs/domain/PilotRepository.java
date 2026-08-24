package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.PilotRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.exception.DataAccessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.AUTH_IDENTITY;
import static com.bonney.hobbs.jooq.Tables.PILOT;

public class PilotRepository {

    private final DSLContext dsl;

    public PilotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(Pilot pilot) {
        try {
            dsl.insertInto(PILOT)
                    .set(PILOT.ID, pilot.getId().value())
                    .set(PILOT.NAME, pilot.getName())
                    .set(PILOT.EMAIL, pilot.getEmail())
                    .onConflict(PILOT.ID)
                    .doUpdate()
                    .set(PILOT.NAME, pilot.getName())
                    .set(PILOT.EMAIL, pilot.getEmail())
                    .execute();
        } catch (DataAccessException e) {
            if (e.getMessage() != null && e.getMessage().contains("PILOT_EMAIL_UNIQUE")) {
                throw new DuplicateEmailException(pilot.getEmail());
            }
            throw e;
        }
    }

    public Optional<Pilot> findById(PilotId id) {
        return Optional.ofNullable(
                dsl.selectFrom(PILOT)
                        .where(PILOT.ID.eq(id.value()))
                        .and(PILOT.DELETED_AT.isNull())
                        .fetchOne())
                .map(PilotRepository::toPilot);
    }

    public Optional<Pilot> findByEmail(String email) {
        return Optional.ofNullable(
                dsl.selectFrom(PILOT)
                        .where(PILOT.EMAIL.eq(email))
                        .and(PILOT.DELETED_AT.isNull())
                        .fetchOne())
                .map(PilotRepository::toPilot);
    }

    public List<Pilot> findAllActive() {
        return dsl.selectFrom(PILOT)
                .where(PILOT.DELETED_AT.isNull())
                .fetch()
                .map(PilotRepository::toPilot);
    }

    // Joined to auth_identity (scoped to the PASSWORD identity, same as findCreatedAtByPilotIds/
    // findLastLoginAtByPilotIds) rather than the two-step batch-lookup GET /admin/pilots used to
    // do, since signedUpAt/lastLoginAt aren't real pilot columns but the pagination/sort here needs
    // to work uniformly across all five sortable columns in one query.
    public List<PilotListRow> findAllActivePage(String sort, String order, int offset, int limit) {
        return dsl.select(PILOT.ID, PILOT.NAME, PILOT.EMAIL, PILOT.DISABLED_AT,
                        AUTH_IDENTITY.CREATED_AT, AUTH_IDENTITY.LAST_LOGIN_AT)
                .from(PILOT)
                .leftJoin(AUTH_IDENTITY)
                .on(AUTH_IDENTITY.PILOT_ID.eq(PILOT.ID).and(AUTH_IDENTITY.TYPE.eq(AuthIdentityType.PASSWORD.name())))
                .where(PILOT.DELETED_AT.isNull())
                .orderBy(sortField(sort, order))
                .limit(limit)
                .offset(offset)
                .fetch()
                .map(PilotRepository::toPilotListRow);
    }

    public int countActive() {
        return dsl.fetchCount(dsl.selectFrom(PILOT).where(PILOT.DELETED_AT.isNull()));
    }

    // Explicit nullsLast()/nullsFirst() rather than relying on each database's own default NULL
    // ordering - H2 and Postgres differ here, the same class of dialect gap already documented in
    // docs/DEPLOYMENT.md's testing-strategy notes.
    private static SortField<?> sortField(String sort, String order) {
        boolean desc = "desc".equalsIgnoreCase(order);
        return switch (sort) {
            case "email" -> desc ? PILOT.EMAIL.desc() : PILOT.EMAIL.asc();
            // disabled_at is NULL for an active pilot - treat NULL as the "false" end of the sort so
            // ascending reads as Active-then-Disabled and descending as Disabled-then-Active, the same
            // as sorting any other boolean column, rather than nulls landing in a fixed spot regardless
            // of direction.
            case "disabled" -> desc ? PILOT.DISABLED_AT.desc().nullsLast() : PILOT.DISABLED_AT.asc().nullsFirst();
            case "signedUpAt" -> desc ? AUTH_IDENTITY.CREATED_AT.desc().nullsLast() : AUTH_IDENTITY.CREATED_AT.asc().nullsLast();
            case "lastLoginAt" -> desc ? AUTH_IDENTITY.LAST_LOGIN_AT.desc().nullsLast() : AUTH_IDENTITY.LAST_LOGIN_AT.asc().nullsLast();
            default -> desc ? PILOT.NAME.desc() : PILOT.NAME.asc();
        };
    }

    public void delete(PilotId id) {
        dsl.update(PILOT)
                .set(PILOT.DELETED_AT, OffsetDateTime.now())
                .where(PILOT.ID.eq(id.value()))
                .execute();
    }

    public void disable(PilotId id) {
        dsl.update(PILOT)
                .set(PILOT.DISABLED_AT, OffsetDateTime.now())
                .where(PILOT.ID.eq(id.value()))
                .execute();
    }

    public void enable(PilotId id) {
        dsl.update(PILOT)
                .set(PILOT.DISABLED_AT, (OffsetDateTime) null)
                .where(PILOT.ID.eq(id.value()))
                .execute();
    }

    public boolean isDisabled(PilotId id) {
        return dsl.fetchExists(
                dsl.selectFrom(PILOT)
                        .where(PILOT.ID.eq(id.value()))
                        .and(PILOT.DISABLED_AT.isNotNull()));
    }

    private static Pilot toPilot(PilotRecord r) {
        return new Pilot(PilotId.from(r.get(PILOT.ID)), r.get(PILOT.NAME), r.get(PILOT.EMAIL),
                r.get(PILOT.DISABLED_AT) != null);
    }

    private static PilotListRow toPilotListRow(Record r) {
        Pilot pilot = new Pilot(PilotId.from(r.get(PILOT.ID)), r.get(PILOT.NAME), r.get(PILOT.EMAIL),
                r.get(PILOT.DISABLED_AT) != null);
        return new PilotListRow(pilot, r.get(AUTH_IDENTITY.CREATED_AT), r.get(AUTH_IDENTITY.LAST_LOGIN_AT));
    }
}
