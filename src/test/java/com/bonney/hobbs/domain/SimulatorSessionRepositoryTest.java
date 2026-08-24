package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class SimulatorSessionRepositoryTest {

    private SimulatorSessionRepository repository;
    private PilotId pilotId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new SimulatorSessionRepository(dsl);

        Pilot pilot = new Pilot(PilotId.random(), "William", "william@example.com");
        new PilotRepository(dsl).save(pilot);
        pilotId = pilot.getId();
    }

    @Test
    void findAllByPilotIdOrdersByDateDescending() {
        SimulatorSession older = new SimulatorSession(SimulatorSessionId.random(), pilotId,
                LocalDate.of(2026, 8, 1), "AATD", 60);
        SimulatorSession newer = new SimulatorSession(SimulatorSessionId.random(), pilotId,
                LocalDate.of(2026, 8, 20), "AATD", 30);
        repository.save(older);
        repository.save(newer);

        List<SimulatorSession> found = repository.findAllByPilotId(pilotId);

        assertThat(found.stream().map(SimulatorSession::getId).toList(), contains(newer.getId(), older.getId()));
    }

    @Test
    void savingAgainUpdatesTheExistingRow() {
        SimulatorSessionId id = SimulatorSessionId.random();
        repository.save(new SimulatorSession(id, pilotId, LocalDate.of(2026, 8, 1), "AATD", 60));

        repository.save(new SimulatorSession(id, pilotId, LocalDate.of(2026, 8, 1), "AATD", 90));

        SimulatorSession found = repository.findAllByPilotId(pilotId).get(0);
        assertThat(found.getMinutes(), is(90));
    }
}
