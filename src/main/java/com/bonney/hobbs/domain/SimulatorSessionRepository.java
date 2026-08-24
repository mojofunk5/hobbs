package com.bonney.hobbs.domain;

import com.bonney.hobbs.jooq.tables.records.SimulatorSessionRecord;
import org.jooq.DSLContext;

import java.util.List;

import static com.bonney.hobbs.jooq.Tables.SIMULATOR_SESSION;

public class SimulatorSessionRepository {

    private final DSLContext dsl;

    public SimulatorSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(SimulatorSession session) {
        dsl.insertInto(SIMULATOR_SESSION)
                .set(SIMULATOR_SESSION.ID, session.getId().value())
                .set(SIMULATOR_SESSION.PILOT_ID, session.getPilotId().value())
                .set(SIMULATOR_SESSION.DATE, session.getDate())
                .set(SIMULATOR_SESSION.FSTD_TYPE, session.getFstdType())
                .set(SIMULATOR_SESSION.MINUTES, session.getMinutes())
                .onConflict(SIMULATOR_SESSION.ID)
                .doUpdate()
                .set(SIMULATOR_SESSION.DATE, session.getDate())
                .set(SIMULATOR_SESSION.FSTD_TYPE, session.getFstdType())
                .set(SIMULATOR_SESSION.MINUTES, session.getMinutes())
                .execute();
    }

    public List<SimulatorSession> findAllByPilotId(PilotId pilotId) {
        return dsl.selectFrom(SIMULATOR_SESSION)
                .where(SIMULATOR_SESSION.PILOT_ID.eq(pilotId.value()))
                .orderBy(SIMULATOR_SESSION.DATE.desc())
                .fetch()
                .map(SimulatorSessionRepository::toSimulatorSession);
    }

    private static SimulatorSession toSimulatorSession(SimulatorSessionRecord record) {
        return new SimulatorSession(
                SimulatorSessionId.from(record.getId()),
                PilotId.from(record.getPilotId()),
                record.getDate(),
                record.getFstdType(),
                record.getMinutes());
    }
}
