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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Runs against a checked-in fixture CSV (src/test/resources/fixtures/ourairports-airports-sample.csv)
 * mirroring OurAirports' real column names, never a live network call - see
 * docs/plans/airfield-picker.md's chunking.
 */
class AirfieldImportJobTest {

    private AirfieldRepository airfieldRepository;
    private AirfieldImportJob job;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:import-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        airfieldRepository = new AirfieldRepository(dsl);
        job = new AirfieldImportJob(airfieldRepository);
    }

    @Test
    void importsOnlyActiveGbFixedWingAirportsAndSkipsEverythingElse() throws IOException {
        AirfieldImportJob.Result result = job.importFrom(fixtureReader());

        // 4 included: Sherburn (small), Leeds Bradford (medium), Heathrow (large), farm strip
        // (small, no ICAO code). 3 excluded: a French large_airport (wrong country), a GB closed
        // airfield, and a GB heliport.
        assertThat(result.processed(), is(4));
        assertThat(result.skipped(), is(3));
        assertThat(airfieldRepository.findAll().size(), is(4));
    }

    @Test
    void excludesNonGbAirports() throws IOException {
        job.importFrom(fixtureReader());

        assertThat(airfieldRepository.search("Charles de Gaulle"), is(List.of()));
    }

    @Test
    void excludesClosedAirfields() throws IOException {
        job.importFrom(fixtureReader());

        assertThat(airfieldRepository.search("Closed Airfield"), is(List.of()));
    }

    @Test
    void excludesNonFixedWingTypesLikeHeliports() throws IOException {
        job.importFrom(fixtureReader());

        assertThat(airfieldRepository.search("Heliport"), is(List.of()));
    }

    @Test
    void importsARowWithNoIcaoCode() throws IOException {
        job.importFrom(fixtureReader());

        Airfield farmStrip = airfieldRepository.search("Farm Strip").get(0);
        assertThat(farmStrip.getIcaoCode(), is(nullValue()));
    }

    @Test
    void carriesTheFullSetOfReferenceFieldsFromTheCsv() throws IOException {
        job.importFrom(fixtureReader());

        Airfield sherburn = airfieldRepository.search("Sherburn").get(0);
        assertThat(sherburn.getIcaoCode(), is("EGCJ"));
        assertThat(sherburn.getName(), is("Sherburn-in-Elmet Airfield"));
        assertThat(sherburn.getMunicipality(), is("Sherburn-in-Elmet"));
        assertThat(sherburn.getIsoCountry(), is("GB"));
        assertThat(sherburn.getIsoRegion(), is("GB-ENG"));
        assertThat(sherburn.getLatitude(), is(53.7883));
        assertThat(sherburn.getLongitude(), is(-1.2225));
        assertThat(sherburn.getElevationFt(), is(26));
        assertThat(sherburn.getType(), is("small_airport"));
        assertThat(sherburn.getSourceName(), is("ourairports"));
        assertThat(sherburn.getSourceId(), is("12345"));
    }

    @Test
    void isIdempotentWhenRunTwiceAgainstTheSameCsv() throws IOException {
        job.importFrom(fixtureReader());
        job.importFrom(fixtureReader());

        assertThat(airfieldRepository.findAll().size(), is(4));
    }

    @Test
    void reRunningUpdatesAnExistingRowsFieldsWithoutChangingItsId() throws IOException {
        job.importFrom(fixtureReader());
        UUID originalId = airfieldRepository.search("Sherburn").get(0).getId().value();

        job.importFrom(new java.io.StringReader(
                "id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region,"
                        + "municipality,scheduled_service,icao_code,iata_code,gps_code,local_code,home_link,"
                        + "wikipedia_link,keywords\n"
                        + "12345,EGCJ,small_airport,Sherburn-in-Elmet Airfield (Renamed),53.79,-1.22,27,EU,GB,GB-ENG,"
                        + "Sherburn-in-Elmet,no,EGCJ,,EGCJ,,,,\n"));

        Airfield updated = airfieldRepository.search("Sherburn").get(0);
        assertThat(updated.getId().value(), is(originalId));
        assertThat(updated.getName(), is("Sherburn-in-Elmet Airfield (Renamed)"));
    }

    private Reader fixtureReader() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/ourairports-airports-sample.csv");
        if (stream == null) {
            throw new IllegalStateException("Fixture CSV not found on classpath");
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
