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

class AircraftRepositoryTest {

    private AircraftRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new AircraftRepository(dsl);
    }

    @Test
    void saveThenFindByIdRoundTripsAllFields() {
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE,
                "CES", "C152", "15280001", "Acme Flying Club", "Acme Ltd", 1978, "1 Lycoming O-235", "Landplane");

        repository.save(aircraft);

        Optional<Aircraft> found = repository.findById(aircraft.getId());
        assertThat(found, is(Optional.of(aircraft)));
        assertThat(found.get().getRegistration(), is("G-ABCD"));
        assertThat(found.get().getMake(), is("Cessna"));
        assertThat(found.get().getModel(), is("152"));
        assertThat(found.get().getEngineCategory(), is(EngineCategory.SINGLE_ENGINE));
        assertThat(found.get().getManufacturerIcao(), is("CES"));
        assertThat(found.get().getTypeCode(), is("C152"));
        assertThat(found.get().getSerialNumber(), is("15280001"));
        assertThat(found.get().getOperator(), is("Acme Flying Club"));
        assertThat(found.get().getOwner(), is("Acme Ltd"));
        assertThat(found.get().getBuilt(), is(1978));
        assertThat(found.get().getEngines(), is("1 Lycoming O-235"));
        assertThat(found.get().getCategoryDescription(), is("Landplane"));
    }

    @Test
    void engineCategoryAndReferenceFieldsCanBeNull() {
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", null,
                null, null, null, null, null, null, null, null);

        repository.save(aircraft);

        Aircraft found = repository.findById(aircraft.getId()).orElseThrow();
        assertThat(found.getEngineCategory(), is((EngineCategory) null));
        assertThat(found.getBuilt(), is((Integer) null));
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertThat(repository.findById(AircraftId.random()), is(Optional.empty()));
    }

    @Test
    void saveUpsertsOnConflict() {
        AircraftId id = AircraftId.random();
        repository.save(anAircraft(id, "G-ABCD", "Cessna", "152"));
        repository.save(anAircraft(id, "G-WXYZ", "Piper", "PA-28"));

        Aircraft found = repository.findById(id).orElseThrow();
        assertThat(found.getRegistration(), is("G-WXYZ"));
        assertThat(found.getModel(), is("PA-28"));
    }

    @Test
    void findAllOrdersByRegistration() {
        repository.save(anAircraft(AircraftId.random(), "G-ZZZZ", "Cessna", "152"));
        repository.save(anAircraft(AircraftId.random(), "G-AAAA", "Piper", "PA-28"));

        List<Aircraft> all = repository.findAll();

        assertThat(all.stream().map(Aircraft::getRegistration).toList(), contains("G-AAAA", "G-ZZZZ"));
    }

    @Test
    void upsertByRegistrationInsertsANewRowWhenRegistrationIsUnknown() {
        repository.upsertByRegistration(anAircraft(AircraftId.random(), "G-ABCD", "Cessna", "152"));

        assertThat(repository.findByRegistration("G-ABCD").orElseThrow().getMake(), is("Cessna"));
    }

    @Test
    void upsertByRegistrationUpdatesTheExistingRowsIdWhenRegistrationAlreadyExists() {
        Aircraft original = anAircraft(AircraftId.random(), "G-ABCD", "Cessna", "152");
        repository.save(original);

        repository.upsertByRegistration(anAircraft(AircraftId.random(), "G-ABCD", "Cessna", "172"));

        Aircraft found = repository.findByRegistration("G-ABCD").orElseThrow();
        assertThat(found.getId(), is(original.getId()));
        assertThat(found.getModel(), is("172"));
        assertThat(repository.findAll(), contains(found));
    }

    @Test
    void findByRegistrationIsCaseInsensitive() {
        repository.save(anAircraft(AircraftId.random(), "G-ABCD", "Cessna", "152"));

        assertThat(repository.findByRegistration("g-abcd").isPresent(), is(true));
    }

    @Test
    void searchMatchesRegistrationMakeOrModelCaseInsensitively() {
        Aircraft cessna = anAircraft(AircraftId.random(), "G-ABCD", "Cessna", "152");
        Aircraft piper = anAircraft(AircraftId.random(), "G-WXYZ", "Piper", "PA-28");
        repository.save(cessna);
        repository.save(piper);

        assertThat(repository.search("cess", 50), contains(cessna));
        assertThat(repository.search("g-", 50), containsInAnyOrder(cessna, piper));
        assertThat(repository.search("nomatch", 50), is(List.of()));
    }

    @Test
    void searchIsCappedAtTheGivenLimit() {
        repository.save(anAircraft(AircraftId.random(), "G-AAAA", "Cessna", "152"));
        repository.save(anAircraft(AircraftId.random(), "G-BBBB", "Cessna", "152"));
        repository.save(anAircraft(AircraftId.random(), "G-CCCC", "Cessna", "152"));

        assertThat(repository.search("cessna", 2).size(), is(2));
    }

    @Test
    void searchByRegistrationOnlyMatchesRegistrationNotMakeOrModel() {
        Aircraft cessna = anAircraft(AircraftId.random(), "G-ABCD", "Cessna", "152");
        Aircraft piper = anAircraft(AircraftId.random(), "G-WXYZ", "Piper", "Warrior");
        repository.save(cessna);
        repository.save(piper);

        assertThat(repository.searchByRegistration("ABCD", 50), contains(cessna));
        assertThat(repository.searchByRegistration("warrior", 50), is(List.of()));
    }

    private static Aircraft anAircraft(AircraftId id, String registration, String make, String model) {
        return new Aircraft(id, registration, make, model, EngineCategory.SINGLE_ENGINE,
                null, null, null, null, null, null, null, null);
    }
}
