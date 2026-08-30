package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.FlightEntryRecord;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
                .set(FLIGHT_ENTRY.DEPARTURE_TIME, entry.getDepartureTime())
                .set(FLIGHT_ENTRY.ARRIVAL_TIME, entry.getArrivalTime())
                .set(FLIGHT_ENTRY.DEPARTURE_AIRFIELD_ID, entry.getDepartureAirfieldId().value())
                .set(FLIGHT_ENTRY.ARRIVAL_AIRFIELD_ID, entry.getArrivalAirfieldId().value())
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
                .set(FLIGHT_ENTRY.DEPARTURE_TIME, entry.getDepartureTime())
                .set(FLIGHT_ENTRY.ARRIVAL_TIME, entry.getArrivalTime())
                .set(FLIGHT_ENTRY.DEPARTURE_AIRFIELD_ID, entry.getDepartureAirfieldId().value())
                .set(FLIGHT_ENTRY.ARRIVAL_AIRFIELD_ID, entry.getArrivalAirfieldId().value())
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

    /**
     * Backs the recent-airfields ranking on GET /airfield?search= (see
     * docs/plans/airfield-picker.md's chunk 5) - the calling pilot's own last {@code limit} distinct
     * departure/arrival airfields, most recently flown first. Walks entries newest-first (same
     * ordering as {@link #findAllByPilotId}) and collects both departure_airfield_id and
     * arrival_airfield_id from each, deduplicating so a pilot who's flown the same airfield
     * repeatedly in a row doesn't crowd out everywhere else they've been - counts *distinct*
     * airfields, not flights.
     */
    public List<AirfieldId> findRecentAirfieldIds(PilotId pilotId, int limit) {
        Result<Record2<UUID, UUID>> records = dsl
                .select(FLIGHT_ENTRY.DEPARTURE_AIRFIELD_ID, FLIGHT_ENTRY.ARRIVAL_AIRFIELD_ID)
                .from(FLIGHT_ENTRY)
                .where(FLIGHT_ENTRY.PILOT_ID.eq(pilotId.value()))
                .orderBy(FLIGHT_ENTRY.DATE.desc(), FLIGHT_ENTRY.DEPARTURE_TIME.desc())
                .fetch();

        Set<UUID> distinctIds = new LinkedHashSet<>();
        for (Record2<UUID, UUID> record : records) {
            if (distinctIds.size() >= limit) {
                break;
            }
            UUID departureAirfieldId = record.value1();
            UUID arrivalAirfieldId = record.value2();
            if (departureAirfieldId != null) {
                distinctIds.add(departureAirfieldId);
            }
            if (arrivalAirfieldId != null) {
                distinctIds.add(arrivalAirfieldId);
            }
        }
        return distinctIds.stream().limit(limit).map(AirfieldId::from).toList();
    }

    private static FlightEntry toFlightEntry(FlightEntryRecord record) {
        return new FlightEntry(
                FlightEntryId.from(record.getId()),
                PilotId.from(record.getPilotId()),
                AircraftId.from(record.getAircraftId()),
                record.getFlightTrackId() == null ? null : FlightTrackId.from(record.getFlightTrackId()),
                record.getDate(),
                record.getDepartureTime(),
                record.getArrivalTime(),
                AirfieldId.from(record.getDepartureAirfieldId()),
                AirfieldId.from(record.getArrivalAirfieldId()),
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
