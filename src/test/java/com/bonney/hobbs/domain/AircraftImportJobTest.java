package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Runs against a checked-in fixture CSV (src/test/resources/fixtures/opensky-aircraft-sample.csv)
 * mirroring OpenSky's real column names, never a live network call - see
 * docs/plans/aircraft-picker.md's chunking.
 */
class AircraftImportJobTest {

    private AircraftRepository aircraftRepository;
    private AircraftImportJob job;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:import-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        aircraftRepository = new AircraftRepository(dsl);
        job = new AircraftImportJob(aircraftRepository);
    }

    @Test
    void importsEveryRowWithARegistrationAndSkipsThoseWithout() throws IOException {
        AircraftImportJob.Result result = job.importFrom(fixtureReader());

        assertThat(result.processed(), is(4));
        assertThat(result.skipped(), is(1));
        assertThat(aircraftRepository.findAll().size(), is(4));
    }

    @Test
    void derivesEngineCategoryFromTheIcaoAircraftTypesEngineCountDigit() throws IOException {
        job.importFrom(fixtureReader());

        assertThat(aircraftRepository.findByRegistration("G-ABCD").orElseThrow().getEngineCategory(),
                is(EngineCategory.SINGLE_ENGINE));
        assertThat(aircraftRepository.findByRegistration("G-TWIN").orElseThrow().getEngineCategory(),
                is(EngineCategory.MULTI_ENGINE));
    }

    @Test
    void leavesEngineCategoryNullWhenIcaoAircraftTypeIsMissingOrUnparseable() throws IOException {
        job.importFrom(fixtureReader());

        assertThat(aircraftRepository.findByRegistration("G-NOENG").orElseThrow().getEngineCategory(), is(nullValue()));
    }

    @Test
    void carriesTheFullSetOfReferenceFieldsFromTheCsv() throws IOException {
        job.importFrom(fixtureReader());

        Aircraft cessna = aircraftRepository.findByRegistration("G-ABCD").orElseThrow();
        assertThat(cessna.getMake(), is("Cessna"));
        assertThat(cessna.getModel(), is("152"));
        assertThat(cessna.getManufacturerIcao(), is("CES"));
        assertThat(cessna.getTypeCode(), is("C152"));
        assertThat(cessna.getSerialNumber(), is("15280001"));
        assertThat(cessna.getOperator(), is("Acme Flying Club"));
        assertThat(cessna.getBuilt(), is(1978));
        assertThat(cessna.getEngines(), is("1 Lycoming O-235"));
        assertThat(cessna.getCategoryDescription(), is("Landplane"));
    }

    @Test
    void isIdempotentWhenRunTwiceAgainstTheSameCsv() throws IOException {
        job.importFrom(fixtureReader());
        job.importFrom(fixtureReader());

        assertThat(aircraftRepository.findAll().size(), is(4));
    }

    @Test
    void reRunningUpdatesAnExistingRowsFieldsWithoutChangingItsId() throws IOException {
        job.importFrom(fixtureReader());
        UUID originalId = aircraftRepository.findByRegistration("G-ABCD").orElseThrow().getId().value();

        job.importFrom(new StringReader(
                "registration,manufacturername,model\nG-ABCD,Cessna,152 II\n"));

        Aircraft updated = aircraftRepository.findByRegistration("G-ABCD").orElseThrow();
        assertThat(updated.getId().value(), is(originalId));
        assertThat(updated.getModel(), is("152 II"));
    }

    @Test
    void anExistingAircraftWithNoMatchingCsvRowIsLeftAlone() throws IOException {
        Aircraft manuallyRegistered = new Aircraft(AircraftId.random(), "G-KEEP", "Cessna", "152",
                EngineCategory.SINGLE_ENGINE, null, null, null, null, null, null, null, null);
        aircraftRepository.save(manuallyRegistered);

        job.importFrom(fixtureReader());

        assertThat(aircraftRepository.findById(manuallyRegistered.getId()), is(Optional.of(manuallyRegistered)));
        assertThat(aircraftRepository.findAll().size(), is(5));
    }

    private Reader fixtureReader() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/opensky-aircraft-sample.csv");
        if (stream == null) {
            throw new IllegalStateException("Fixture CSV not found on classpath");
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
