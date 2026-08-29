package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.FlightEntryRecord;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.FLIGHT_ENTRY;

public class FlightEntryRepository {

    private final DSLContext dsl;

    public FlightEntryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(FlightEntry entry) {
        dsl.insertInto(FLIGHT_ENTRY)
                .set(FLIGHT_ENTRY.ID, entry.getId().value())
                .set(FLIGHT_ENTRY.PILOT_ID, entry.getPilotId().value())
                .set(FLIGHT_ENTRY.AIRCRAFT_ID, entry.getAircraftId().value())
                .set(FLIGHT_ENTRY.FLIGHT_TRACK_ID, entry.getFlightTrackId().map(FlightTrackId::value).orElse(null))
                .set(FLIGHT_ENTRY.DATE, entry.getDate())
                .set(FLIGHT_ENTRY.DEPARTURE_PLACE, entry.getDeparturePlace())
                .set(FLIGHT_ENTRY.DEPARTURE_TIME, entry.getDepartureTime())
                .set(FLIGHT_ENTRY.ARRIVAL_PLACE, entry.getArrivalPlace())
                .set(FLIGHT_ENTRY.ARRIVAL_TIME, entry.getArrivalTime())
                .set(FLIGHT_ENTRY.PILOT_IN_COMMAND_ID, entry.getPilotInCommandId().value())
                .set(FLIGHT_ENTRY.CO_PILOT_ID, entry.getCoPilotId().map(PilotId::value).orElse(null))
                .set(FLIGHT_ENTRY.SINGLE_ENGINE_MINUTES, entry.getSingleEngineMinutes())
                .set(FLIGHT_ENTRY.MULTI_ENGINE_MINUTES, entry.getMultiEngineMinutes())
                .set(FLIGHT_ENTRY.TOTAL_MINUTES, entry.getTotalMinutes())
                .set(FLIGHT_ENTRY.NIGHT_MINUTES, entry.getNightMinutes())
                .set(FLIGHT_ENTRY.IFR_MINUTES, entry.getIfrMinutes())
                .set(FLIGHT_ENTRY.CROSS_COUNTRY_MINUTES, entry.getCrossCountryMinutes())
                .set(FLIGHT_ENTRY.PILOT_IN_COMMAND_MINUTES, entry.getPilotInCommandMinutes())
                .set(FLIGHT_ENTRY.CO_PILOT_MINUTES, entry.getCoPilotMinutes())
                .set(FLIGHT_ENTRY.DUAL_MINUTES, entry.getDualMinutes())
                .set(FLIGHT_ENTRY.INSTRUCTOR_MINUTES, entry.getInstructorMinutes())
                .set(FLIGHT_ENTRY.DAY_LANDINGS, entry.getDayLandings())
                .set(FLIGHT_ENTRY.NIGHT_LANDINGS, entry.getNightLandings())
                .set(FLIGHT_ENTRY.REMARKS, entry.getRemarks())
                .onDuplicateKeyUpdate()
                .set(FLIGHT_ENTRY.AIRCRAFT_ID, entry.getAircraftId().value())
                .set(FLIGHT_ENTRY.FLIGHT_TRACK_ID, entry.getFlightTrackId().map(FlightTrackId::value).orElse(null))
                .set(FLIGHT_ENTRY.DATE, entry.getDate())
                .set(FLIGHT_ENTRY.DEPARTURE_PLACE, entry.getDeparturePlace())
                .set(FLIGHT_ENTRY.DEPARTURE_TIME, entry.getDepartureTime())
                .set(FLIGHT_ENTRY.ARRIVAL_PLACE, entry.getArrivalPlace())
                .set(FLIGHT_ENTRY.ARRIVAL_TIME, entry.getArrivalTime())
                .set(FLIGHT_ENTRY.PILOT_IN_COMMAND_ID, entry.getPilotInCommandId().value())
                .set(FLIGHT_ENTRY.CO_PILOT_ID, entry.getCoPilotId().map(PilotId::value).orElse(null))
                .set(FLIGHT_ENTRY.SINGLE_ENGINE_MINUTES, entry.getSingleEngineMinutes())
                .set(FLIGHT_ENTRY.MULTI_ENGINE_MINUTES, entry.getMultiEngineMinutes())
                .set(FLIGHT_ENTRY.TOTAL_MINUTES, entry.getTotalMinutes())
                .set(FLIGHT_ENTRY.NIGHT_MINUTES, entry.getNightMinutes())
                .set(FLIGHT_ENTRY.IFR_MINUTES, entry.getIfrMinutes())
                .set(FLIGHT_ENTRY.CROSS_COUNTRY_MINUTES, entry.getCrossCountryMinutes())
                .set(FLIGHT_ENTRY.PILOT_IN_COMMAND_MINUTES, entry.getPilotInCommandMinutes())
                .set(FLIGHT_ENTRY.CO_PILOT_MINUTES, entry.getCoPilotMinutes())
                .set(FLIGHT_ENTRY.DUAL_MINUTES, entry.getDualMinutes())
                .set(FLIGHT_ENTRY.INSTRUCTOR_MINUTES, entry.getInstructorMinutes())
                .set(FLIGHT_ENTRY.DAY_LANDINGS, entry.getDayLandings())
                .set(FLIGHT_ENTRY.NIGHT_LANDINGS, entry.getNightLandings())
                .set(FLIGHT_ENTRY.REMARKS, entry.getRemarks())
                .execute();
    }

    public Optional<FlightEntry> findById(FlightEntryId id) {
        return Optional.ofNullable(
                dsl.selectFrom(FLIGHT_ENTRY)
                        .where(FLIGHT_ENTRY.ID.eq(id.value()))
                        .fetchOne())
                .map(FlightEntryRepository::toFlightEntry);
    }

    public List<FlightEntry> findAllByPilotId(PilotId pilotId) {
        return dsl.selectFrom(FLIGHT_ENTRY)
                .where(FLIGHT_ENTRY.PILOT_ID.eq(pilotId.value()))
                .orderBy(FLIGHT_ENTRY.DATE.desc(), FLIGHT_ENTRY.DEPARTURE_TIME.desc())
                .fetch()
                .map(FlightEntryRepository::toFlightEntry);
    }

    private static FlightEntry toFlightEntry(FlightEntryRecord record) {
        return new FlightEntry(
                FlightEntryId.from(record.getId()),
                PilotId.from(record.getPilotId()),
                AircraftId.from(record.getAircraftId()),
                record.getFlightTrackId() == null ? null : FlightTrackId.from(record.getFlightTrackId()),
                record.getDate(),
                record.getDeparturePlace(),
                record.getDepartureTime(),
                record.getArrivalPlace(),
                record.getArrivalTime(),
                PilotId.from(record.getPilotInCommandId()),
                record.getCoPilotId() == null ? null : PilotId.from(record.getCoPilotId()),
                record.getSingleEngineMinutes(),
                record.getMultiEngineMinutes(),
                record.getTotalMinutes(),
                record.getNightMinutes(),
                record.getIfrMinutes(),
                record.getCrossCountryMinutes(),
                record.getPilotInCommandMinutes(),
                record.getCoPilotMinutes(),
                record.getDualMinutes(),
                record.getInstructorMinutes(),
                record.getDayLandings(),
                record.getNightLandings(),
                record.getRemarks());
    }
}
