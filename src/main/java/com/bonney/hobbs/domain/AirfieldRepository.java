package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.AirfieldRecord;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.AIRFIELD;

public class AirfieldRepository {

    private final DSLContext dsl;

    public AirfieldRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(Airfield airfield) {
        dsl.insertInto(AIRFIELD)
                .set(AIRFIELD.ID, airfield.getId().value())
                .set(AIRFIELD.ICAO_CODE, airfield.getIcaoCode())
                .set(AIRFIELD.NAME, airfield.getName())
                .set(AIRFIELD.MUNICIPALITY, airfield.getMunicipality())
                .set(AIRFIELD.ISO_COUNTRY, airfield.getIsoCountry())
                .set(AIRFIELD.ISO_REGION, airfield.getIsoRegion())
                .set(AIRFIELD.LATITUDE, airfield.getLatitude())
                .set(AIRFIELD.LONGITUDE, airfield.getLongitude())
                .set(AIRFIELD.ELEVATION_FT, airfield.getElevationFt())
                .set(AIRFIELD.TYPE, airfield.getType())
                .set(AIRFIELD.SOURCE_NAME, airfield.getSourceName())
                .set(AIRFIELD.SOURCE_ID, airfield.getSourceId())
                .onConflict(AIRFIELD.ID)
                .doUpdate()
                .set(AIRFIELD.ICAO_CODE, airfield.getIcaoCode())
                .set(AIRFIELD.NAME, airfield.getName())
                .set(AIRFIELD.MUNICIPALITY, airfield.getMunicipality())
                .set(AIRFIELD.ISO_COUNTRY, airfield.getIsoCountry())
                .set(AIRFIELD.ISO_REGION, airfield.getIsoRegion())
                .set(AIRFIELD.LATITUDE, airfield.getLatitude())
                .set(AIRFIELD.LONGITUDE, airfield.getLongitude())
                .set(AIRFIELD.ELEVATION_FT, airfield.getElevationFt())
                .set(AIRFIELD.TYPE, airfield.getType())
                .set(AIRFIELD.SOURCE_NAME, airfield.getSourceName())
                .set(AIRFIELD.SOURCE_ID, airfield.getSourceId())
                .execute();
    }

    /**
     * Upsert-by-(sourceName, sourceId) for the OurAirports import job (see
     * docs/plans/airfield-picker.md) - their own row id is the only field guaranteed present and
     * stable across re-imports, since a handful of small GB strips have no ICAO code at all. A CSV
     * row matching an existing (sourceName, sourceId) updates that row's fields in place (its
     * {@link AirfieldId} is never touched by the conflict clause, so it stays stable even though not
     * known ahead of time - important once FlightEntry rows can reference it); a row not yet present
     * is inserted fresh under {@code airfield.getId()}.
     */
    public void upsertBySource(Airfield airfield) {
        dsl.insertInto(AIRFIELD)
                .set(AIRFIELD.ID, airfield.getId().value())
                .set(AIRFIELD.ICAO_CODE, airfield.getIcaoCode())
                .set(AIRFIELD.NAME, airfield.getName())
                .set(AIRFIELD.MUNICIPALITY, airfield.getMunicipality())
                .set(AIRFIELD.ISO_COUNTRY, airfield.getIsoCountry())
                .set(AIRFIELD.ISO_REGION, airfield.getIsoRegion())
                .set(AIRFIELD.LATITUDE, airfield.getLatitude())
                .set(AIRFIELD.LONGITUDE, airfield.getLongitude())
                .set(AIRFIELD.ELEVATION_FT, airfield.getElevationFt())
                .set(AIRFIELD.TYPE, airfield.getType())
                .set(AIRFIELD.SOURCE_NAME, airfield.getSourceName())
                .set(AIRFIELD.SOURCE_ID, airfield.getSourceId())
                .onConflict(AIRFIELD.SOURCE_NAME, AIRFIELD.SOURCE_ID)
                .doUpdate()
                .set(AIRFIELD.ICAO_CODE, airfield.getIcaoCode())
                .set(AIRFIELD.NAME, airfield.getName())
                .set(AIRFIELD.MUNICIPALITY, airfield.getMunicipality())
                .set(AIRFIELD.ISO_COUNTRY, airfield.getIsoCountry())
                .set(AIRFIELD.ISO_REGION, airfield.getIsoRegion())
                .set(AIRFIELD.LATITUDE, airfield.getLatitude())
                .set(AIRFIELD.LONGITUDE, airfield.getLongitude())
                .set(AIRFIELD.ELEVATION_FT, airfield.getElevationFt())
                .set(AIRFIELD.TYPE, airfield.getType())
                .execute();
    }

    public Optional<Airfield> findById(AirfieldId id) {
        return Optional.ofNullable(
                dsl.selectFrom(AIRFIELD)
                        .where(AIRFIELD.ID.eq(id.value()))
                        .fetchOne())
                .map(AirfieldRepository::toAirfield);
    }

    /**
     * Backs GET /airfield?search= with an empty/missing search - the full GB set is small enough
     * (~1,200 rows) that "everything, alphabetical" is a sane response, unlike
     * {@link AircraftRepository#findAll}'s ~600k-row equivalent which the aircraft endpoint never
     * calls unfiltered. Ordered by name.
     */
    public List<Airfield> findAll() {
        return dsl.selectFrom(AIRFIELD)
                .orderBy(AIRFIELD.NAME)
                .fetch()
                .map(AirfieldRepository::toAirfield);
    }

    /**
     * Backs GET /airfield?search= - case-insensitive substring match on name OR exact/prefix match
     * on icaoCode (matches OurAirports' own ident/gps_code/local_code convention of the ICAO code
     * being the primary identifier), combined in one query per docs/plans/airfield-picker.md.
     * Ordered by name; no result cap, unlike aircraft's search - ~1,200 rows total is small enough
     * that a matching subset never needs capping.
     */
    public List<Airfield> search(String search) {
        String pattern = "%" + search.toLowerCase() + "%";
        String prefixPattern = search.toLowerCase() + "%";
        return dsl.selectFrom(AIRFIELD)
                .where(org.jooq.impl.DSL.lower(AIRFIELD.NAME).like(pattern)
                        .or(org.jooq.impl.DSL.lower(AIRFIELD.ICAO_CODE).like(prefixPattern)))
                .orderBy(AIRFIELD.NAME)
                .fetch()
                .map(AirfieldRepository::toAirfield);
    }

    private static Airfield toAirfield(AirfieldRecord record) {
        return new Airfield(
                AirfieldId.from(record.getId()),
                record.getIcaoCode(),
                record.getName(),
                record.getMunicipality(),
                record.getIsoCountry(),
                record.getIsoRegion(),
                record.getLatitude(),
                record.getLongitude(),
                record.getElevationFt(),
                record.getType(),
                record.getSourceName(),
                record.getSourceId());
    }
}
