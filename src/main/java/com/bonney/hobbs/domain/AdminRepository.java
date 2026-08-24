package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.Tables;
import org.jooq.DSLContext;

import java.util.UUID;

public class AdminRepository {

    private final DSLContext dsl;

    public AdminRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean isEmpty() {
        return dsl.fetchCount(Tables.ADMIN) == 0;
    }

    public boolean isAdmin(PilotId pilotId) {
        return dsl.fetchExists(Tables.ADMIN, Tables.ADMIN.PILOT_ID.eq(pilotId.value()));
    }

    public void makeAdmin(PilotId pilotId) {
        dsl.insertInto(Tables.ADMIN)
                .set(Tables.ADMIN.PILOT_ID, pilotId.value())
                .onDuplicateKeyIgnore()
                .execute();
    }
}
