package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.AircraftRecord;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.AIRCRAFT;

public class AircraftRepository {

    private final DSLContext dsl;

    public AircraftRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(Aircraft aircraft) {
        dsl.insertInto(AIRCRAFT)
                .set(AIRCRAFT.ID, aircraft.getId().value())
                .set(AIRCRAFT.REGISTRATION, aircraft.getRegistration())
                .set(AIRCRAFT.MAKE, aircraft.getMake())
                .set(AIRCRAFT.MODEL, aircraft.getModel())
                .set(AIRCRAFT.ENGINE_CATEGORY, aircraft.getEngineCategory().name())
                .onConflict(AIRCRAFT.ID)
                .doUpdate()
                .set(AIRCRAFT.REGISTRATION, aircraft.getRegistration())
                .set(AIRCRAFT.MAKE, aircraft.getMake())
                .set(AIRCRAFT.MODEL, aircraft.getModel())
                .set(AIRCRAFT.ENGINE_CATEGORY, aircraft.getEngineCategory().name())
                .execute();
    }

    public Optional<Aircraft> findById(AircraftId id) {
        return Optional.ofNullable(
                dsl.selectFrom(AIRCRAFT)
                        .where(AIRCRAFT.ID.eq(id.value()))
                        .fetchOne())
                .map(AircraftRepository::toAircraft);
    }

    public List<Aircraft> findAll() {
        return dsl.selectFrom(AIRCRAFT)
                .orderBy(AIRCRAFT.REGISTRATION)
                .fetch()
                .map(AircraftRepository::toAircraft);
    }

    private static Aircraft toAircraft(AircraftRecord record) {
        return new Aircraft(
                AircraftId.from(record.getId()),
                record.getRegistration(),
                record.getMake(),
                record.getModel(),
                EngineCategory.valueOf(record.getEngineCategory()));
    }
}
