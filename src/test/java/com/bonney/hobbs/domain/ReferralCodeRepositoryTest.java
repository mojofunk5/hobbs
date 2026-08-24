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

class ReferralCodeRepositoryTest {

    private ReferralCodeRepository repository;
    private PilotId createdBy;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new ReferralCodeRepository(dsl);

        Pilot pilot = randomPilot();
        new PilotRepository(dsl).save(pilot);
        createdBy = pilot.getId();
    }

    @Test
    void unexpiredUnusedCodeCanBeFoundByCode() {
        repository.save(new ReferralCode("code", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));

        ReferralCode found = repository.findUnusedByCode("code").orElseThrow();

        assertThat(found.getInvitedEmail(), is("alice@example.com"));
    }

    @Test
    void expiredCodeIsNotFoundAsUnused() {
        repository.save(new ReferralCode("code", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().minusMinutes(1)));

        assertThat(repository.findUnusedByCode("code"), is(Optional.empty()));
    }

    @Test
    void expireUnusedForEmailExpiresPendingCodesForThatEmailOnly() {
        repository.save(new ReferralCode("first", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));
        repository.save(new ReferralCode("second", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));
        repository.save(new ReferralCode("other", createdBy, OffsetDateTime.now(), "bob@example.com", OffsetDateTime.now().plusHours(1)));

        repository.expireUnusedForEmail("alice@example.com");

        assertThat(repository.findUnusedByCode("first"), is(Optional.empty()));
        assertThat(repository.findUnusedByCode("second"), is(Optional.empty()));
        assertThat(repository.findUnusedByCode("other").isPresent(), is(true));
    }

    @Test
    void expireUnusedForEmailRemovesTheOldCodeFromListUnusedEvenAfterTheReplacementIsUsed() {
        repository.save(new ReferralCode("old-code", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));

        repository.expireUnusedForEmail("alice@example.com");
        repository.save(new ReferralCode("new-code", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));
        repository.markUsed("new-code", createdBy);

        assertThat(repository.listUnused().stream().anyMatch(c -> c.getInvitedEmail().equals("alice@example.com")), is(false));
    }

    @Test
    void listUnusedCollapsesRepeatInvitesToTheSameEmailToTheNewestOne() {
        OffsetDateTime older = OffsetDateTime.now().minusHours(2);
        OffsetDateTime newer = OffsetDateTime.now().minusHours(1);
        repository.save(new ReferralCode("older-code", createdBy, older, "alice@example.com", older.plusHours(24)));
        repository.save(new ReferralCode("newer-code", createdBy, newer, "alice@example.com", newer.plusHours(24)));
        repository.save(new ReferralCode("bob-code", createdBy, newer, "bob@example.com", newer.plusHours(24)));

        var unused = repository.listUnused();

        assertThat(unused, hasSize(2));
        assertThat(unused.stream().map(ReferralCode::getCode).toList(), containsInAnyOrder("newer-code", "bob-code"));
    }

    @Test
    void cancelUnusedForEmailRemovesItFromListUnusedEntirely() {
        repository.save(new ReferralCode("code", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));

        repository.cancelUnusedForEmail("alice@example.com");

        assertThat(repository.listUnused(), is(empty()));
    }

    @Test
    void cancelledCodeCannotBeUsedToRegister() {
        repository.save(new ReferralCode("code", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));

        repository.cancelUnusedForEmail("alice@example.com");

        assertThat(repository.findUnusedByCode("code"), is(Optional.empty()));
    }

    @Test
    void cancelUnusedForEmailAffectsOnlyThatEmail() {
        repository.save(new ReferralCode("alice-code", createdBy, OffsetDateTime.now(), "alice@example.com", OffsetDateTime.now().plusHours(1)));
        repository.save(new ReferralCode("bob-code", createdBy, OffsetDateTime.now(), "bob@example.com", OffsetDateTime.now().plusHours(1)));

        repository.cancelUnusedForEmail("alice@example.com");

        assertThat(repository.findUnusedByCode("bob-code").isPresent(), is(true));
    }
}
