package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.bonney.hobbs.TestPilots.randomPilot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PasswordResetCodeRepositoryTest {

    private PasswordResetCodeRepository repository;
    private PilotId pilotId;
    private PilotId otherPilotId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new PasswordResetCodeRepository(dsl);

        PilotRepository pilotRepository = new PilotRepository(dsl);
        Pilot pilot = randomPilot();
        pilotRepository.save(pilot);
        pilotId = pilot.getId();
        Pilot otherPilot = randomPilot();
        pilotRepository.save(otherPilot);
        otherPilotId = otherPilot.getId();
    }

    @Test
    void unexpiredUnusedCodeCanBeFoundByPilotIdAndCode() {
        repository.save(new PasswordResetCode(PasswordResetCodeId.random(), pilotId, "123456", OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30)));

        PasswordResetCode found = repository.findUnusedByPilotIdAndCode(pilotId, "123456").orElseThrow();

        assertThat(found.getCode(), is("123456"));
    }

    @Test
    void expiredCodeIsNotFoundAsUnused() {
        repository.save(new PasswordResetCode(PasswordResetCodeId.random(), pilotId, "123456", OffsetDateTime.now(), OffsetDateTime.now().minusMinutes(1)));

        assertThat(repository.findUnusedByPilotIdAndCode(pilotId, "123456"), is(Optional.empty()));
    }

    @Test
    void codeIsScopedToItsOwnPilot() {
        repository.save(new PasswordResetCode(PasswordResetCodeId.random(), pilotId, "123456", OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30)));

        assertThat(repository.findUnusedByPilotIdAndCode(otherPilotId, "123456"), is(Optional.empty()));
    }

    @Test
    void markUsedMakesTheCodeUnfindable() {
        PasswordResetCode code = new PasswordResetCode(PasswordResetCodeId.random(), pilotId, "123456", OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30));
        repository.save(code);

        repository.markUsed(code.getId());

        assertThat(repository.findUnusedByPilotIdAndCode(pilotId, "123456"), is(Optional.empty()));
    }

    @Test
    void invalidateUnusedForPilotAffectsOnlyThatPilotsUnusedCodes() {
        repository.save(new PasswordResetCode(PasswordResetCodeId.random(), pilotId, "111111", OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30)));
        repository.save(new PasswordResetCode(PasswordResetCodeId.random(), otherPilotId, "222222", OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30)));

        repository.invalidateUnusedForPilot(pilotId);

        assertThat(repository.findUnusedByPilotIdAndCode(pilotId, "111111"), is(Optional.empty()));
        assertThat(repository.findUnusedByPilotIdAndCode(otherPilotId, "222222").isPresent(), is(true));
    }
}
