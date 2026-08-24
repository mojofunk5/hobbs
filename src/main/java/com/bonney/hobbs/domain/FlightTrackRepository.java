package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.FlightTrackRecord;
import org.jooq.DSLContext;

import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.FLIGHT_TRACK;

public class FlightTrackRepository {

    private final DSLContext dsl;

    public FlightTrackRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(FlightTrack track) {
        dsl.insertInto(FLIGHT_TRACK)
                .set(FLIGHT_TRACK.ID, track.getId().value())
                .set(FLIGHT_TRACK.PILOT_ID, track.getPilotId().value())
                .set(FLIGHT_TRACK.STARTED_AT, track.getStartedAt())
                .set(FLIGHT_TRACK.ENDED_AT, track.getEndedAt().orElse(null))
                .set(FLIGHT_TRACK.POINTS_JSON, track.getPointsJson())
                .onConflict(FLIGHT_TRACK.ID)
                .doUpdate()
                .set(FLIGHT_TRACK.ENDED_AT, track.getEndedAt().orElse(null))
                .set(FLIGHT_TRACK.POINTS_JSON, track.getPointsJson())
                .execute();
    }

    public Optional<FlightTrack> findById(FlightTrackId id) {
        return Optional.ofNullable(
                dsl.selectFrom(FLIGHT_TRACK)
                        .where(FLIGHT_TRACK.ID.eq(id.value()))
                        .fetchOne())
                .map(FlightTrackRepository::toFlightTrack);
    }

    private static FlightTrack toFlightTrack(FlightTrackRecord record) {
        return new FlightTrack(
                FlightTrackId.from(record.getId()),
                PilotId.from(record.getPilotId()),
                record.getStartedAt(),
                record.getEndedAt(),
                record.getPointsJson());
    }
}
