package com.bonney.hobbs.domain;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Re-runnable reconciliation job that upserts {@link Aircraft} reference data from OpenSky's
 * aircraftDatabase.csv (or any CSV sharing its column names) - see docs/plans/aircraft-picker.md.
 * Idempotent: re-running against the same or a newer snapshot just re-upserts the same rows by
 * registration ({@link AircraftRepository#upsertByRegistration}). A row already in the table with
 * no matching CSV row is left alone, never deleted - not this job's concern at all, since it only
 * ever reads the CSV forwards.
 *
 * <p>Wired up as the {@code import-aircraft} CLI subcommand (see HobbsApplication), triggered on
 * demand rather than on a tight schedule - OpenSky's own crowdsourced database is currently paused,
 * so there is nothing to gain from polling it automatically. Not a live network call itself: takes
 * an already-downloaded CSV file, same "tests against a fixture, not the network" split the plan
 * calls for.
 */
public class AircraftImportJob {

    private static final Logger logger = LoggerFactory.getLogger(AircraftImportJob.class);

    // OpenSky's icaoaircrafttype: {surface}{engineCount}{engineType}, e.g. "L2P" = landplane, 2
    // engines, piston. Only the middle digit (engine count) matters here.
    private static final Pattern ENGINE_COUNT = Pattern.compile("^.(\\d).$");

    private final AircraftRepository aircraftRepository;

    public AircraftImportJob(AircraftRepository aircraftRepository) {
        this.aircraftRepository = aircraftRepository;
    }

    public record Result(int processed, int skipped) {
    }

    public Result importFrom(Reader csvReader) throws IOException {
        int processed = 0;
        int skipped = 0;
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = format.parse(csvReader)) {
            for (CSVRecord record : parser) {
                String registration = trimToNull(record, "registration");
                if (registration == null) {
                    skipped++;
                    continue;
                }
                Aircraft aircraft = new Aircraft(
                        AircraftId.random(),
                        registration.toUpperCase(),
                        trimToNull(record, "manufacturername"),
                        trimToNull(record, "model"),
                        engineCategoryFrom(trimToNull(record, "icaoaircrafttype")),
                        trimToNull(record, "manufacturericao"),
                        trimToNull(record, "typecode"),
                        trimToNull(record, "serialnumber"),
                        trimToNull(record, "operator"),
                        trimToNull(record, "owner"),
                        parseYear(trimToNull(record, "built")),
                        trimToNull(record, "engines"),
                        trimToNull(record, "categoryDescription"));
                aircraftRepository.upsertByRegistration(aircraft);
                processed++;
            }
        }
        logger.info("Aircraft import complete: processed={}, skipped (no registration)={}", processed, skipped);
        return new Result(processed, skipped);
    }

    private static EngineCategory engineCategoryFrom(String icaoAircraftType) {
        if (icaoAircraftType == null) {
            return null;
        }
        Matcher matcher = ENGINE_COUNT.matcher(icaoAircraftType);
        if (!matcher.matches()) {
            return null;
        }
        int engineCount = Integer.parseInt(matcher.group(1));
        if (engineCount <= 0) {
            return null;
        }
        return engineCount == 1 ? EngineCategory.SINGLE_ENGINE : EngineCategory.MULTI_ENGINE;
    }

    private static Integer parseYear(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
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
