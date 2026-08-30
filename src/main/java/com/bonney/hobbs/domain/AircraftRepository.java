package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.AircraftRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.AIRCRAFT;

public class AircraftRepository {

    private final DSLContext dsl;

    public AircraftRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(Aircraft aircraft) {
        String engineCategory = aircraft.getEngineCategory() == null ? null : aircraft.getEngineCategory().name();
        dsl.insertInto(AIRCRAFT)
                .set(AIRCRAFT.ID, aircraft.getId().value())
                .set(AIRCRAFT.REGISTRATION, aircraft.getRegistration())
                .set(AIRCRAFT.MAKE, aircraft.getMake())
                .set(AIRCRAFT.MODEL, aircraft.getModel())
                .set(AIRCRAFT.ENGINE_CATEGORY, engineCategory)
                .set(AIRCRAFT.MANUFACTURER_ICAO, aircraft.getManufacturerIcao())
                .set(AIRCRAFT.TYPE_CODE, aircraft.getTypeCode())
                .set(AIRCRAFT.SERIAL_NUMBER, aircraft.getSerialNumber())
                .set(AIRCRAFT.OPERATOR, aircraft.getOperator())
                .set(AIRCRAFT.OWNER, aircraft.getOwner())
                .set(AIRCRAFT.BUILT, aircraft.getBuilt())
                .set(AIRCRAFT.ENGINES, aircraft.getEngines())
                .set(AIRCRAFT.CATEGORY_DESCRIPTION, aircraft.getCategoryDescription())
                .onConflict(AIRCRAFT.ID)
                .doUpdate()
                .set(AIRCRAFT.REGISTRATION, aircraft.getRegistration())
                .set(AIRCRAFT.MAKE, aircraft.getMake())
                .set(AIRCRAFT.MODEL, aircraft.getModel())
                .set(AIRCRAFT.ENGINE_CATEGORY, engineCategory)
                .set(AIRCRAFT.MANUFACTURER_ICAO, aircraft.getManufacturerIcao())
                .set(AIRCRAFT.TYPE_CODE, aircraft.getTypeCode())
                .set(AIRCRAFT.SERIAL_NUMBER, aircraft.getSerialNumber())
                .set(AIRCRAFT.OPERATOR, aircraft.getOperator())
                .set(AIRCRAFT.OWNER, aircraft.getOwner())
                .set(AIRCRAFT.BUILT, aircraft.getBuilt())
                .set(AIRCRAFT.ENGINES, aircraft.getEngines())
                .set(AIRCRAFT.CATEGORY_DESCRIPTION, aircraft.getCategoryDescription())
                .execute();
    }

    /**
     * Upsert-by-registration for the OpenSky import job (see docs/plans/aircraft-picker.md) - the
     * natural key both systems agree on, case-insensitive exact match (registration already has a
     * unique constraint, from before this plan). A CSV row matching an existing registration
     * updates that row's reference fields in place (its {@link AircraftId} is never touched by the
     * conflict clause, so it stays stable even though it's not known ahead of time - important
     * since FlightEntry rows may already reference it); a registration not yet present is inserted
     * fresh under {@code aircraft.getId()}. One round trip per call, so the import job can batch
     * these rather than doing a select-then-insert-or-update per CSV row.
     */
    public void upsertByRegistration(Aircraft aircraft) {
        String engineCategory = aircraft.getEngineCategory() == null ? null : aircraft.getEngineCategory().name();
        dsl.insertInto(AIRCRAFT)
                .set(AIRCRAFT.ID, aircraft.getId().value())
                .set(AIRCRAFT.REGISTRATION, aircraft.getRegistration())
                .set(AIRCRAFT.MAKE, aircraft.getMake())
                .set(AIRCRAFT.MODEL, aircraft.getModel())
                .set(AIRCRAFT.ENGINE_CATEGORY, engineCategory)
                .set(AIRCRAFT.MANUFACTURER_ICAO, aircraft.getManufacturerIcao())
                .set(AIRCRAFT.TYPE_CODE, aircraft.getTypeCode())
                .set(AIRCRAFT.SERIAL_NUMBER, aircraft.getSerialNumber())
                .set(AIRCRAFT.OPERATOR, aircraft.getOperator())
                .set(AIRCRAFT.OWNER, aircraft.getOwner())
                .set(AIRCRAFT.BUILT, aircraft.getBuilt())
                .set(AIRCRAFT.ENGINES, aircraft.getEngines())
                .set(AIRCRAFT.CATEGORY_DESCRIPTION, aircraft.getCategoryDescription())
                .onConflict(AIRCRAFT.REGISTRATION)
                .doUpdate()
                .set(AIRCRAFT.MAKE, aircraft.getMake())
                .set(AIRCRAFT.MODEL, aircraft.getModel())
                .set(AIRCRAFT.ENGINE_CATEGORY, engineCategory)
                .set(AIRCRAFT.MANUFACTURER_ICAO, aircraft.getManufacturerIcao())
                .set(AIRCRAFT.TYPE_CODE, aircraft.getTypeCode())
                .set(AIRCRAFT.SERIAL_NUMBER, aircraft.getSerialNumber())
                .set(AIRCRAFT.OPERATOR, aircraft.getOperator())
                .set(AIRCRAFT.OWNER, aircraft.getOwner())
                .set(AIRCRAFT.BUILT, aircraft.getBuilt())
                .set(AIRCRAFT.ENGINES, aircraft.getEngines())
                .set(AIRCRAFT.CATEGORY_DESCRIPTION, aircraft.getCategoryDescription())
                .execute();
    }

    public Optional<Aircraft> findById(AircraftId id) {
        return Optional.ofNullable(
                dsl.selectFrom(AIRCRAFT)
                        .where(AIRCRAFT.ID.eq(id.value()))
                        .fetchOne())
                .map(AircraftRepository::toAircraft);
    }

    public Optional<Aircraft> findByRegistration(String registration) {
        return Optional.ofNullable(
                dsl.selectFrom(AIRCRAFT)
                        .where(AIRCRAFT.REGISTRATION.equalIgnoreCase(registration))
                        .fetchOne())
                .map(AircraftRepository::toAircraft);
    }

    public List<Aircraft> findAll() {
        return dsl.selectFrom(AIRCRAFT)
                .orderBy(AIRCRAFT.REGISTRATION)
                .fetch()
                .map(AircraftRepository::toAircraft);
    }

    /**
     * Backs GET /aircraft?search= for the Browse Aircraft page - case-insensitive substring match
     * across registration/make/model, capped at {@code limit} rows ordered by registration. Unlike
     * {@link #findAll}, the caller (the endpoint) is responsible for requiring a non-blank search
     * term - this method has no "return everything" mode, since against the full imported dataset
     * that would mean ~600k rows.
     */
    public List<Aircraft> search(String search, int limit) {
        String pattern = likePattern(search);
        return fetchMatching(DSL.lower(AIRCRAFT.REGISTRATION).like(pattern)
                .or(DSL.lower(AIRCRAFT.MAKE).like(pattern))
                .or(DSL.lower(AIRCRAFT.MODEL).like(pattern)), limit);
    }

    /**
     * Backs GET /aircraft?search=&registrationOnly=true for the flight-entry picker - unlike
     * {@link #search}, deliberately narrower: a pilot picking an aircraft they already know the
     * tail number of wants exact-ish registration matches, not incidental hits on a make/model
     * substring (e.g. searching "warrior" would otherwise surface every Piper Warrior in the
     * dataset). Same case-insensitive substring/limit/ordering semantics otherwise.
     */
    public List<Aircraft> searchByRegistration(String search, int limit) {
        return fetchMatching(DSL.lower(AIRCRAFT.REGISTRATION).like(likePattern(search)), limit);
    }

    private List<Aircraft> fetchMatching(Condition condition, int limit) {
        return dsl.selectFrom(AIRCRAFT)
                .where(condition)
                .orderBy(AIRCRAFT.REGISTRATION)
                .limit(limit)
                .fetch()
                .map(AircraftRepository::toAircraft);
    }

    private static String likePattern(String search) {
        return "%" + search.toLowerCase() + "%";
    }

    private static Aircraft toAircraft(AircraftRecord record) {
        EngineCategory engineCategory = record.getEngineCategory() == null
                ? null : EngineCategory.valueOf(record.getEngineCategory());
        return new Aircraft(
                AircraftId.from(record.getId()),
                record.getRegistration(),
                record.getMake(),
                record.getModel(),
                engineCategory,
                record.getManufacturerIcao(),
                record.getTypeCode(),
                record.getSerialNumber(),
                record.getOperator(),
                record.getOwner(),
                record.getBuilt(),
                record.getEngines(),
                record.getCategoryDescription());
    }
}
