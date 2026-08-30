package com.bonney.hobbs.domain;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.bonney.hobbs.jooq.Tables.ACCOUNT;
import static com.bonney.hobbs.jooq.Tables.AUTH_IDENTITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PilotRepositoryTest {

    private PilotRepository repository;
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:repo-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).load().migrate();

        dsl = DSL.using(dataSource, SQLDialect.H2);
        repository = new PilotRepository(dsl);
    }

    // Inserts account/auth_identity directly via jOOQ (bypassing Accounts/AuthIdentityRepository,
    // which always stamp created_at as "now") so signedUpAt/lastLoginAt can be backdated
    // deterministically for sort-order assertions, rather than relying on real sleeps between saves.
    private Pilot savePilot(String name, String email, boolean disabled, OffsetDateTime signedUpAt, OffsetDateTime lastLoginAt) {
        Pilot pilot = new Pilot(PilotId.random(), name, null);
        repository.save(pilot);
        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.PILOT_ID, pilot.getId().value())
                .set(ACCOUNT.EMAIL, email)
                .set(ACCOUNT.DISABLED_AT, disabled ? OffsetDateTime.now() : null)
                .execute();
        dsl.insertInto(AUTH_IDENTITY)
                .set(AUTH_IDENTITY.ID, UUID.randomUUID())
                .set(AUTH_IDENTITY.PILOT_ID, pilot.getId().value())
                .set(AUTH_IDENTITY.TYPE, AuthIdentityType.PASSWORD.name())
                .set(AUTH_IDENTITY.IDENTIFIER, email)
                .set(AUTH_IDENTITY.HASHED_CREDENTIAL, "hashed")
                .set(AUTH_IDENTITY.CREATED_AT, signedUpAt)
                .set(AUTH_IDENTITY.LAST_LOGIN_AT, lastLoginAt)
                .execute();
        return pilot;
    }

    private List<String> names(List<PilotListRow> rows) {
        return rows.stream().map(r -> r.pilot().getName()).toList();
    }

    @Test
    void sortsByNameAscendingByDefault() {
        savePilot("Charlie", "charlie@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Alice", "alice@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Bob", "bob@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());

        assertThat(names(repository.findAllActivePage("name", "asc", 0, 10)), contains("Alice", "Bob", "Charlie"));
    }

    @Test
    void sortsByNameDescending() {
        savePilot("Charlie", "charlie2@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Alice", "alice2@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Bob", "bob2@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());

        assertThat(names(repository.findAllActivePage("name", "desc", 0, 10)), contains("Charlie", "Bob", "Alice"));
    }

    @Test
    void sortsByEmail() {
        savePilot("Zed", "aaa@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Amy", "zzz@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());

        List<PilotListRow> rows = repository.findAllActivePage("email", "asc", 0, 10);

        assertThat(rows.stream().map(PilotListRow::email).toList(), contains("aaa@example.com", "zzz@example.com"));
    }

    @Test
    void sortsBySignedUpAt() {
        OffsetDateTime earlier = OffsetDateTime.now().minusDays(2);
        OffsetDateTime later = OffsetDateTime.now().minusDays(1);
        savePilot("Later", "later@example.com", false, later, later);
        savePilot("Earlier", "earlier@example.com", false, earlier, earlier);

        assertThat(names(repository.findAllActivePage("signedUpAt", "asc", 0, 10)), contains("Earlier", "Later"));
    }

    @Test
    void sortsByLastLoginAt() {
        OffsetDateTime earlier = OffsetDateTime.now().minusDays(2);
        OffsetDateTime later = OffsetDateTime.now().minusDays(1);
        savePilot("RecentLogin", "recent@example.com", false, earlier, later);
        savePilot("OldLogin", "old@example.com", false, earlier, earlier);

        assertThat(names(repository.findAllActivePage("lastLoginAt", "asc", 0, 10)), contains("OldLogin", "RecentLogin"));
    }

    @Test
    void sortsByDisabledActiveFirstAscendingDisabledFirstDescending() {
        savePilot("Disabled", "disabled@example.com", true, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Active", "active@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());

        assertThat(names(repository.findAllActivePage("disabled", "asc", 0, 10)), contains("Active", "Disabled"));
        assertThat(names(repository.findAllActivePage("disabled", "desc", 0, 10)), contains("Disabled", "Active"));
    }

    @Test
    void nullLastLoginAtSortsLastRegardlessOfDirection() {
        OffsetDateTime hasLogin = OffsetDateTime.now().minusDays(1);
        savePilot("HasLogin", "haslogin@example.com", false, OffsetDateTime.now(), hasLogin);
        savePilot("NeverLoggedIn", "never@example.com", false, OffsetDateTime.now(), null);

        assertThat(names(repository.findAllActivePage("lastLoginAt", "asc", 0, 10)), contains("HasLogin", "NeverLoggedIn"));
        assertThat(names(repository.findAllActivePage("lastLoginAt", "desc", 0, 10)), contains("HasLogin", "NeverLoggedIn"));
    }

    @Test
    void respectsLimitAndOffsetForPagination() {
        savePilot("Alice", "alice3@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Bob", "bob3@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Charlie", "charlie3@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());

        assertThat(names(repository.findAllActivePage("name", "asc", 0, 2)), contains("Alice", "Bob"));
        assertThat(names(repository.findAllActivePage("name", "asc", 2, 2)), contains("Charlie"));
    }

    @Test
    void countActiveMatchesTotalPilots() {
        savePilot("Alice", "alice4@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());
        savePilot("Bob", "bob4@example.com", false, OffsetDateTime.now(), OffsetDateTime.now());

        assertThat(repository.countActive(), is(2));
    }

    @Test
    void anUnclaimedPilotWithNoAccountAppearsInTheListWithNullEmailAndDisabled() {
        Pilot creator = new Pilot(PilotId.random(), "William", null);
        repository.save(creator);
        Pilot unclaimed = new Pilot(PilotId.random(), "Louis", creator.getId());
        repository.save(unclaimed);

        List<PilotListRow> rows = repository.findAllActivePage("name", "asc", 0, 10);

        PilotListRow row = rows.stream().filter(r -> r.pilot().getId().equals(unclaimed.getId())).findFirst().orElseThrow();
        assertThat(row.email(), is(nullValue()));
        assertThat(row.disabled(), is(nullValue()));
    }

    @Test
    void updateNameChangesTheStoredName() {
        Pilot pilot = new Pilot(PilotId.random(), "Old Name", null);
        repository.save(pilot);

        repository.updateName(pilot.getId(), "New Name");

        assertThat(repository.findById(pilot.getId()).orElseThrow().getName(), is("New Name"));
    }

    @Test
    void savePersistsCreatedBy() {
        Pilot creator = new Pilot(PilotId.random(), "William", null);
        repository.save(creator);
        Pilot unclaimed = new Pilot(PilotId.random(), "Louis", creator.getId());

        repository.save(unclaimed);

        assertThat(repository.findById(unclaimed.getId()).orElseThrow().getCreatedBy(), is(creator.getId()));
    }

    private List<String> knownToNames(PilotId callerId, String search) {
        return repository.findKnownTo(callerId, search).stream().map(Pilot::getName).toList();
    }

    private FlightEntry flightEntry(PilotId ownerId, PilotId pilotInCommandId, PilotId coPilotId, AircraftId aircraftId) {
        return new FlightEntry(FlightEntryId.random(), ownerId, aircraftId, null, LocalDate.now(),
                "EGCM", OffsetDateTime.now(), "EGCM", OffsetDateTime.now(), pilotInCommandId, coPilotId,
                30, 0, 30, 0, 0, 0, 30, 0, 0, 0, 1, 0, null);
    }

    @Test
    void findKnownToAlwaysIncludesTheCallerThemselves() {
        Pilot william = new Pilot(PilotId.random(), "William", null);
        repository.save(william);

        assertThat(knownToNames(william.getId(), null), contains("William"));
    }

    @Test
    void findKnownToIncludesPilotsTheCallerCreated() {
        Pilot william = new Pilot(PilotId.random(), "William", null);
        repository.save(william);
        Pilot louis = new Pilot(PilotId.random(), "Louis", william.getId());
        repository.save(louis);

        assertThat(knownToNames(william.getId(), null), containsInAnyOrder("William", "Louis"));
    }

    @Test
    void findKnownToIncludesPilotsFlownWithAsPilotInCommandOrCoPilot() {
        Pilot william = new Pilot(PilotId.random(), "William", null);
        repository.save(william);
        Pilot instructor = new Pilot(PilotId.random(), "Instructor Smith", null);
        repository.save(instructor);
        Pilot coPilot = new Pilot(PilotId.random(), "Amy Co-Pilot", null);
        repository.save(coPilot);
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);
        new AircraftRepository(dsl).save(aircraft);
        new FlightEntryRepository(dsl).save(
                flightEntry(william.getId(), instructor.getId(), coPilot.getId(), aircraft.getId()));

        assertThat(knownToNames(william.getId(), null),
                containsInAnyOrder("William", "Instructor Smith", "Amy Co-Pilot"));
    }

    @Test
    void findKnownToExcludesPilotsUnrelatedToTheCaller() {
        Pilot william = new Pilot(PilotId.random(), "William", null);
        repository.save(william);
        Pilot stranger = new Pilot(PilotId.random(), "Stranger", null);
        repository.save(stranger);

        assertThat(knownToNames(william.getId(), null), not(hasItem("Stranger")));
    }

    @Test
    void findKnownToFiltersByCaseInsensitiveNameSubstring() {
        Pilot william = new Pilot(PilotId.random(), "William", null);
        repository.save(william);
        Pilot louis = new Pilot(PilotId.random(), "Louis", william.getId());
        repository.save(louis);

        assertThat(knownToNames(william.getId(), "lou"), contains("Louis"));
        assertThat(knownToNames(william.getId(), "zzz"), is(empty()));
    }

    @Test
    void findKnownToOnlyShowsAPilotFlownWithMultipleTimesOnce() {
        Pilot william = new Pilot(PilotId.random(), "William", null);
        repository.save(william);
        Pilot instructor = new Pilot(PilotId.random(), "Instructor Smith", null);
        repository.save(instructor);
        Aircraft aircraft = new Aircraft(AircraftId.random(), "G-ABCD", "Cessna", "152", EngineCategory.SINGLE_ENGINE);
        new AircraftRepository(dsl).save(aircraft);
        FlightEntryRepository flightEntryRepository = new FlightEntryRepository(dsl);
        flightEntryRepository.save(flightEntry(william.getId(), instructor.getId(), null, aircraft.getId()));
        flightEntryRepository.save(flightEntry(william.getId(), instructor.getId(), null, aircraft.getId()));

        assertThat(knownToNames(william.getId(), null), containsInAnyOrder("William", "Instructor Smith"));
    }
}
