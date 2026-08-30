package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class AirfieldRepositoryTest {

    private AirfieldRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new AirfieldRepository(dsl);
    }

    @Test
    void saveThenFindByIdRoundTripsAllFields() {
        Airfield airfield = new Airfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield",
                "Sherburn-in-Elmet", "GB", "GB-ENG", 53.7883, -1.2225, 26, "small_airport", "ourairports", "12345");

        repository.save(airfield);

        Optional<Airfield> found = repository.findById(airfield.getId());
        assertThat(found, is(Optional.of(airfield)));
        assertThat(found.get().getIcaoCode(), is("EGCJ"));
        assertThat(found.get().getName(), is("Sherburn-in-Elmet Airfield"));
        assertThat(found.get().getMunicipality(), is("Sherburn-in-Elmet"));
        assertThat(found.get().getIsoCountry(), is("GB"));
        assertThat(found.get().getIsoRegion(), is("GB-ENG"));
        assertThat(found.get().getLatitude(), is(53.7883));
        assertThat(found.get().getLongitude(), is(-1.2225));
        assertThat(found.get().getElevationFt(), is(26));
        assertThat(found.get().getType(), is("small_airport"));
        assertThat(found.get().getSourceName(), is("ourairports"));
        assertThat(found.get().getSourceId(), is("12345"));
    }

    @Test
    void icaoCodeAndElevationCanBeNull() {
        Airfield airfield = new Airfield(AirfieldId.random(), null, "Some Farm Strip", null, "GB", "GB-ENG",
                53.8, -1.2, null, "small_airport", "ourairports", "99999");

        repository.save(airfield);

        Airfield found = repository.findById(airfield.getId()).orElseThrow();
        assertThat(found.getIcaoCode(), is((String) null));
        assertThat(found.getMunicipality(), is((String) null));
        assertThat(found.getElevationFt(), is((Integer) null));
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertThat(repository.findById(AirfieldId.random()), is(Optional.empty()));
    }

    @Test
    void saveUpsertsOnConflict() {
        AirfieldId id = AirfieldId.random();
        repository.save(anAirfield(id, "EGCJ", "Sherburn-in-Elmet Airfield", "1"));
        repository.save(anAirfield(id, "EGNM", "Leeds Bradford Airport", "1"));

        Airfield found = repository.findById(id).orElseThrow();
        assertThat(found.getIcaoCode(), is("EGNM"));
        assertThat(found.getName(), is("Leeds Bradford Airport"));
    }

    @Test
    void findAllOrdersByName() {
        repository.save(anAirfield(AirfieldId.random(), "EGNM", "Zulu Airfield", "1"));
        repository.save(anAirfield(AirfieldId.random(), "EGCJ", "Alpha Airfield", "2"));

        List<Airfield> all = repository.findAll();

        assertThat(all.stream().map(Airfield::getName).toList(), contains("Alpha Airfield", "Zulu Airfield"));
    }

    @Test
    void upsertBySourceInsertsANewRowWhenSourceIsUnknown() {
        repository.upsertBySource(anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield", "1"));

        assertThat(repository.search("Sherburn").get(0).getIcaoCode(), is("EGCJ"));
    }

    @Test
    void upsertBySourceUpdatesTheExistingRowsIdWhenSourceAlreadyExists() {
        Airfield original = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield", "1");
        repository.save(original);

        repository.upsertBySource(anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield (Renamed)", "1"));

        Airfield found = repository.search("Sherburn").get(0);
        assertThat(found.getId(), is(original.getId()));
        assertThat(found.getName(), is("Sherburn-in-Elmet Airfield (Renamed)"));
        assertThat(repository.findAll(), contains(found));
    }

    @Test
    void searchMatchesNameSubstringOrIcaoCodePrefixCaseInsensitively() {
        Airfield sherburn = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield", "1");
        Airfield leeds = anAirfield(AirfieldId.random(), "EGNM", "Leeds Bradford Airport", "2");
        repository.save(sherburn);
        repository.save(leeds);

        assertThat(repository.search("sherburn"), contains(sherburn));
        assertThat(repository.search("egcj"), contains(sherburn));
        assertThat(repository.search("leeds"), contains(leeds));
        assertThat(repository.search("nomatch"), is(List.of()));
    }

    @Test
    void searchIcaoCodeMatchIsPrefixOnlyNotSubstring() {
        Airfield sherburn = anAirfield(AirfieldId.random(), "EGCJ", "Sherburn-in-Elmet Airfield", "1");
        repository.save(sherburn);

        assertThat(repository.search("EGC"), contains(sherburn));
        assertThat(repository.search("GCJ"), is(List.of()));
    }

    @Test
    void findAllAndSearchReturnEveryMatchWithNoResultCap() {
        for (int i = 0; i < 60; i++) {
            repository.save(anAirfield(AirfieldId.random(), "EG" + String.format("%02d", i), "Airfield " + i,
                    String.valueOf(i)));
        }

        assertThat(repository.findAll().size(), is(60));
        assertThat(repository.search("airfield").size(), is(60));
    }

    private static Airfield anAirfield(AirfieldId id, String icaoCode, String name, String sourceId) {
        return new Airfield(id, icaoCode, name, "Somewhere", "GB", "GB-ENG", 53.0, -1.0, 100,
                "small_airport", "ourairports", sourceId);
    }
}
