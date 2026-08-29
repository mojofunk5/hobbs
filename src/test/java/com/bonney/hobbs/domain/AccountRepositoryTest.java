package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.bonney.hobbs.TestPilots.randomPilot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountRepositoryTest {

    private AccountRepository repository;
    private PilotRepository pilotRepository;
    private PilotId pilotId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new AccountRepository(dsl);
        pilotRepository = new PilotRepository(dsl);

        Pilot pilot = randomPilot();
        pilotRepository.save(pilot);
        pilotId = pilot.getId();
    }

    private PilotId newPilotId() {
        Pilot pilot = randomPilot();
        pilotRepository.save(pilot);
        return pilot.getId();
    }

    @Test
    void createdAccountCanBeFoundByPilotIdAndIsNotDisabled() {
        repository.create(pilotId, "alice@example.com");

        Account account = repository.get(pilotId).orElseThrow();

        assertThat(account.getEmail(), is("alice@example.com"));
        assertThat(account.isDisabled(), is(false));
    }

    @Test
    void pilotWithNoAccountIsNotFound() {
        assertThat(repository.get(pilotId), is(Optional.empty()));
    }

    @Test
    void createdAccountCanBeFoundByEmail() {
        repository.create(pilotId, "alice@example.com");

        Account account = repository.findByEmail("alice@example.com").orElseThrow();

        assertThat(account.getPilotId(), is(pilotId));
    }

    @Test
    void creatingASecondAccountWithTheSameEmailThrowsDuplicateEmailException() {
        repository.create(pilotId, "alice@example.com");
        PilotId otherPilotId = newPilotId();

        assertThrows(DuplicateEmailException.class, () -> repository.create(otherPilotId, "alice@example.com"));
    }

    @Test
    void updatingEmailToOneAlreadyUsedByAnotherAccountThrowsDuplicateEmailException() {
        repository.create(pilotId, "alice@example.com");
        PilotId otherPilotId = newPilotId();
        repository.create(otherPilotId, "bob@example.com");

        assertThrows(DuplicateEmailException.class, () -> repository.updateEmail(otherPilotId, "alice@example.com"));
    }

    @Test
    void updateEmailChangesTheStoredEmail() {
        repository.create(pilotId, "alice@example.com");

        repository.updateEmail(pilotId, "alice-new@example.com");

        assertThat(repository.get(pilotId).orElseThrow().getEmail(), is("alice-new@example.com"));
    }

    @Test
    void disableMarksTheAccountDisabled() {
        repository.create(pilotId, "alice@example.com");

        repository.disable(pilotId);

        assertThat(repository.isDisabled(pilotId), is(true));
    }

    @Test
    void enableClearsADisabledAccount() {
        repository.create(pilotId, "alice@example.com");
        repository.disable(pilotId);

        repository.enable(pilotId);

        assertThat(repository.isDisabled(pilotId), is(false));
    }

    @Test
    void aPilotWithNoAccountIsNotDisabled() {
        assertThat(repository.isDisabled(pilotId), is(false));
    }
}
