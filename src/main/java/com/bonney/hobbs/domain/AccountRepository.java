package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.Tables;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import java.time.OffsetDateTime;
import java.util.Optional;

public class AccountRepository {

    private final DSLContext dsl;

    public AccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void create(PilotId pilotId, String email) {
        try {
            dsl.insertInto(Tables.ACCOUNT)
                    .set(Tables.ACCOUNT.PILOT_ID, pilotId.value())
                    .set(Tables.ACCOUNT.EMAIL, email)
                    .execute();
        } catch (DataAccessException e) {
            if (e.getMessage() != null && e.getMessage().contains("ACCOUNT_EMAIL_UNIQUE")) {
                throw new DuplicateEmailException(email);
            }
            throw e;
        }
    }

    public void updateEmail(PilotId pilotId, String newEmail) {
        try {
            dsl.update(Tables.ACCOUNT)
                    .set(Tables.ACCOUNT.EMAIL, newEmail)
                    .where(Tables.ACCOUNT.PILOT_ID.eq(pilotId.value()))
                    .execute();
        } catch (DataAccessException e) {
            if (e.getMessage() != null && e.getMessage().contains("ACCOUNT_EMAIL_UNIQUE")) {
                throw new DuplicateEmailException(newEmail);
            }
            throw e;
        }
    }

    public void delete(PilotId pilotId) {
        dsl.deleteFrom(Tables.ACCOUNT)
                .where(Tables.ACCOUNT.PILOT_ID.eq(pilotId.value()))
                .execute();
    }

    public void disable(PilotId pilotId) {
        dsl.update(Tables.ACCOUNT)
                .set(Tables.ACCOUNT.DISABLED_AT, OffsetDateTime.now())
                .where(Tables.ACCOUNT.PILOT_ID.eq(pilotId.value()))
                .execute();
    }

    public void enable(PilotId pilotId) {
        dsl.update(Tables.ACCOUNT)
                .set(Tables.ACCOUNT.DISABLED_AT, (OffsetDateTime) null)
                .where(Tables.ACCOUNT.PILOT_ID.eq(pilotId.value()))
                .execute();
    }

    public boolean isDisabled(PilotId pilotId) {
        return dsl.fetchExists(
                dsl.selectFrom(Tables.ACCOUNT)
                        .where(Tables.ACCOUNT.PILOT_ID.eq(pilotId.value()))
                        .and(Tables.ACCOUNT.DISABLED_AT.isNotNull()));
    }

    public Optional<Account> findByEmail(String email) {
        return dsl.selectFrom(Tables.ACCOUNT)
                .where(Tables.ACCOUNT.EMAIL.eq(email))
                .fetchOptional()
                .map(r -> new Account(PilotId.from(r.get(Tables.ACCOUNT.PILOT_ID)), r.get(Tables.ACCOUNT.EMAIL),
                        r.get(Tables.ACCOUNT.DISABLED_AT) != null));
    }

    public Optional<Account> get(PilotId pilotId) {
        return dsl.selectFrom(Tables.ACCOUNT)
                .where(Tables.ACCOUNT.PILOT_ID.eq(pilotId.value()))
                .fetchOptional()
                .map(r -> new Account(PilotId.from(r.get(Tables.ACCOUNT.PILOT_ID)), r.get(Tables.ACCOUNT.EMAIL),
                        r.get(Tables.ACCOUNT.DISABLED_AT) != null));
    }
}
