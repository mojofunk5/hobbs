package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.Tables;
import com.bonney.hobbs.jooq.tables.records.PilotRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SortField;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.bonney.hobbs.jooq.Tables.ACCOUNT;
import static com.bonney.hobbs.jooq.Tables.AUTH_IDENTITY;
import static com.bonney.hobbs.jooq.Tables.PILOT;

public class PilotRepository {

    private final DSLContext dsl;

    public PilotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(Pilot pilot) {
        dsl.insertInto(PILOT)
                .set(PILOT.ID, pilot.getId().value())
                .set(PILOT.NAME, pilot.getName())
                .set(PILOT.CREATED_BY, pilot.getCreatedBy() == null ? null : pilot.getCreatedBy().value())
                .onConflict(PILOT.ID)
                .doUpdate()
                .set(PILOT.NAME, pilot.getName())
                .execute();
    }

    public void updateName(PilotId id, String name) {
        dsl.update(PILOT)
                .set(PILOT.NAME, name)
                .where(PILOT.ID.eq(id.value()))
                .execute();
    }

    public Optional<Pilot> findById(PilotId id) {
        return Optional.ofNullable(
                dsl.selectFrom(PILOT)
                        .where(PILOT.ID.eq(id.value()))
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
    // to work uniformly across all five sortable columns in one query. Also LEFT JOINed to account
    // for email/disabled - a pilot with no account (an unclaimed record) shows up with both null
    // rather than being hidden from the list.
    public List<PilotListRow> findAllActivePage(String sort, String order, int offset, int limit) {
        return dsl.select(PILOT.ID, PILOT.NAME, PILOT.CREATED_BY, ACCOUNT.EMAIL, ACCOUNT.DISABLED_AT,
                        AUTH_IDENTITY.CREATED_AT, AUTH_IDENTITY.LAST_LOGIN_AT)
                .from(PILOT)
                .leftJoin(ACCOUNT)
                .on(ACCOUNT.PILOT_ID.eq(PILOT.ID))
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
            case "email" -> desc ? ACCOUNT.EMAIL.desc().nullsLast() : ACCOUNT.EMAIL.asc().nullsLast();
            // disabled_at is NULL for an active pilot (or one with no account) - treat NULL as the
            // "false" end of the sort so ascending reads as Active-then-Disabled and descending as
            // Disabled-then-Active, the same as sorting any other boolean column, rather than nulls
            // landing in a fixed spot regardless of direction.
            case "disabled" -> desc ? ACCOUNT.DISABLED_AT.desc().nullsLast() : ACCOUNT.DISABLED_AT.asc().nullsFirst();
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

    private static Pilot toPilot(PilotRecord r) {
        UUID createdBy = r.get(PILOT.CREATED_BY);
        return new Pilot(PilotId.from(r.get(PILOT.ID)), r.get(PILOT.NAME), createdBy == null ? null : PilotId.from(createdBy));
    }

    private static PilotListRow toPilotListRow(Record r) {
        UUID createdBy = r.get(PILOT.CREATED_BY);
        Pilot pilot = new Pilot(PilotId.from(r.get(PILOT.ID)), r.get(PILOT.NAME), createdBy == null ? null : PilotId.from(createdBy));
        Boolean disabled = r.get(ACCOUNT.DISABLED_AT) != null ? Boolean.TRUE
                : r.get(ACCOUNT.EMAIL) != null ? Boolean.FALSE : null;
        return new PilotListRow(pilot, r.get(ACCOUNT.EMAIL), disabled, r.get(AUTH_IDENTITY.CREATED_AT), r.get(AUTH_IDENTITY.LAST_LOGIN_AT));
    }
}
