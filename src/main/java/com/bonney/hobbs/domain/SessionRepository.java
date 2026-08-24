package com.bonney.hobbs.domain;

import org.jooq.DSLContext;

import java.time.OffsetDateTime;
import java.util.Optional;

import static com.bonney.hobbs.jooq.Tables.SESSION;

public class SessionRepository {

    private final DSLContext dsl;

    public SessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(SessionId id, PilotId pilotId, OffsetDateTime createdAt) {
        dsl.insertInto(SESSION)
                .set(SESSION.ID, id.value())
                .set(SESSION.PILOT_ID, pilotId.value())
                .set(SESSION.CREATED_AT, createdAt)
                .set(SESSION.LAST_ACCESSED_AT, createdAt)
                .execute();
    }

    // Two steps rather than a single atomic UPDATE ... RETURNING - the tiny race window (a session
    // expiring between the check and the touch) doesn't matter at this app's traffic level, and this
    // way works identically whether the query runs against H2 (MODE=PostgreSQL) or real Postgres
    // without depending on RETURNING support being consistent across both.
    public Optional<PilotId> findIfUnexpiredAndTouch(SessionId id, int ttlHours) {
        Optional<PilotId> pilotId = dsl.select(SESSION.PILOT_ID)
                .from(SESSION)
                .where(SESSION.ID.eq(id.value()))
                .and(SESSION.LAST_ACCESSED_AT.gt(OffsetDateTime.now().minusHours(ttlHours)))
                .fetchOptional(r -> PilotId.from(r.get(SESSION.PILOT_ID)));

        if (pilotId.isPresent()) {
            dsl.update(SESSION)
                    .set(SESSION.LAST_ACCESSED_AT, OffsetDateTime.now())
                    .where(SESSION.ID.eq(id.value()))
                    .execute();
        }

        return pilotId;
    }

    public void deleteAllForPilot(PilotId pilotId) {
        dsl.deleteFrom(SESSION)
                .where(SESSION.PILOT_ID.eq(pilotId.value()))
                .execute();
    }

    // Run periodically by ScheduledCleanupJobs so this table doesn't grow forever with sessions
    // nobody's used in a long time.
    public void deleteExpired(int ttlHours) {
        dsl.deleteFrom(SESSION)
                .where(SESSION.LAST_ACCESSED_AT.lt(OffsetDateTime.now().minusHours(ttlHours)))
                .execute();
    }
}
