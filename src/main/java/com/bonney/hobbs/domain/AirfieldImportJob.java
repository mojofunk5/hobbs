package com.bonney.hobbs.domain;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/**
 * Re-runnable reconciliation job that upserts {@link Airfield} reference data from OurAirports'
 * airports.csv (or any CSV sharing its column names) - see docs/plans/airfield-picker.md. Idempotent:
 * re-running against the same or a newer snapshot just re-upserts the same rows by
 * (sourceName, sourceId) ({@link AirfieldRepository#upsertBySource}). A row already in the table
 * with no matching CSV row is left alone, never deleted - same reasoning as
 * {@link AircraftImportJob}.
 *
 * <p>Filters to {@code iso_country = GB}, drops {@code type = closed}, and keeps only
 * small/medium/large fixed-wing airports (not heliports, seaplane bases, or balloonports) - see the
 * plan doc's Confirmed decisions. Wired up as the {@code import-airfields} CLI subcommand (see
 * HobbsApplication). Not a live network call itself: takes an already-downloaded CSV file, same
 * "tests against a fixture, not the network" split the aircraft-picker plan established.
 */
public class AirfieldImportJob {

    private static final Logger logger = LoggerFactory.getLogger(AirfieldImportJob.class);

    private static final String SOURCE_NAME = "ourairports";
    private static final String INCLUDED_COUNTRY = "GB";
    private static final Set<String> INCLUDED_TYPES = Set.of("small_airport", "medium_airport", "large_airport");

    private final AirfieldRepository airfieldRepository;

    public AirfieldImportJob(AirfieldRepository airfieldRepository) {
        this.airfieldRepository = airfieldRepository;
    }

    public record Result(int processed, int skipped) {
    }

    public Result importFrom(Reader csvReader) throws IOException {
        int processed = 0;
        int skipped = 0;
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = format.parse(csvReader)) {
            for (CSVRecord record : parser) {
                if (!isIncluded(record)) {
                    skipped++;
                    continue;
                }
                String sourceId = trimToNull(record, "id");
                Double latitude = parseDouble(trimToNull(record, "latitude_deg"));
                Double longitude = parseDouble(trimToNull(record, "longitude_deg"));
                if (sourceId == null || latitude == null || longitude == null) {
                    skipped++;
                    continue;
                }
                Airfield airfield = new Airfield(
                        AirfieldId.random(),
                        trimToNull(record, "icao_code"),
                        trimToNull(record, "name"),
                        trimToNull(record, "municipality"),
                        trimToNull(record, "iso_country"),
                        trimToNull(record, "iso_region"),
                        latitude,
                        longitude,
                        parseInt(trimToNull(record, "elevation_ft")),
                        trimToNull(record, "type"),
                        SOURCE_NAME,
                        sourceId);
                airfieldRepository.upsertBySource(airfield);
                processed++;
            }
        }
        logger.info("Airfield import complete: processed={}, skipped={}", processed, skipped);
        return new Result(processed, skipped);
    }

    private static boolean isIncluded(CSVRecord record) {
        String isoCountry = trimToNull(record, "iso_country");
        String type = trimToNull(record, "type");
        return INCLUDED_COUNTRY.equals(isoCountry) && type != null && INCLUDED_TYPES.contains(type);
    }

    private static Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            // elevation_ft is sometimes a float string (e.g. "26.0") in OurAirports' own data.
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
