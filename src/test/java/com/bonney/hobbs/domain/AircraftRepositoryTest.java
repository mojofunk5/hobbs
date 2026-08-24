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
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);

        repository.save(aircraft);

        Optional<Aircraft> found = repository.findById(aircraft.getId());
        assertThat(found, is(Optional.of(aircraft)));
        assertThat(found.get().getRegistration(), is("G-ABCD"));
        assertThat(found.get().getMake(), is("Cessna"));
        assertThat(found.get().getModel(), is("152"));
        assertThat(found.get().getEngineCategory(), is(EngineCategory.SINGLE_ENGINE));
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertThat(repository.findById(AircraftId.random()), is(Optional.empty()));
    }

    @Test
    void saveUpsertsOnConflict() {
        AircraftId id = AircraftId.random();
        repository.save(new Aircraft(id, "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE));
        repository.save(new Aircraft(id, "G-WXYZ", "Piper", "PA-28", EngineCategory.SINGLE_ENGINE));

        Aircraft found = repository.findById(id).orElseThrow();
        assertThat(found.getRegistration(), is("G-WXYZ"));
        assertThat(found.getModel(), is("PA-28"));
    }

    @Test
    void findAllOrdersByRegistration() {
        repository.save(new Aircraft(AircraftId.random(), "G-ZZZZ", "Cessna", "152", EngineCategory.SINGLE_ENGINE));
        repository.save(new Aircraft(AircraftId.random(), "G-AAAA", "Piper", "PA-28", EngineCategory.SINGLE_ENGINE));

        List<Aircraft> all = repository.findAll();

        assertThat(all.stream().map(Aircraft::getRegistration).toList(), contains("G-AAAA", "G-ZZZZ"));
    }
}
