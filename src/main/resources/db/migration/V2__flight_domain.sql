-- Core logbook domain: aircraft, GPS-recorded tracks, and the flight entries themselves.
-- See CLAUDE.md for the CAP804/FCL.050 field mapping this is built against.

CREATE TABLE aircraft (
    id              UUID          PRIMARY KEY,
    registration    VARCHAR(20)   NOT NULL,
    make            VARCHAR(100)  NOT NULL,
    model           VARCHAR(100)  NOT NULL,
    engine_category VARCHAR(20)   NOT NULL,
    CONSTRAINT aircraft_registration_unique UNIQUE (registration)
);

-- Raw GPS recording from the mobile app. Points are stored as a single JSON blob (see
-- FlightTrack's Javadoc) rather than one row per point - deliberately simple for the MVP, since the
-- whole track is only ever read back as a unit. Revisit as a normalized point table only if
-- server-side per-point querying becomes a real need.
CREATE TABLE flight_track (
    id           UUID          PRIMARY KEY,
    pilot_id     UUID          NOT NULL REFERENCES pilot(id),
    started_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at     TIMESTAMP WITH TIME ZONE,
    points_json  TEXT          NOT NULL
);

CREATE INDEX idx_flight_track_pilot_id ON flight_track(pilot_id);

-- One row per CAP804/FCL.050 logbook line. flight_track_id is nullable and optional by design -
-- GPS recording is a fast-path onto this same entry, never a requirement (see FlightEntry's
-- Javadoc). Every duration is stored in whole minutes, not a float hours value, to avoid rounding
-- drift accumulating across hundreds of entries.
CREATE TABLE flight_entry (
    id                     UUID          PRIMARY KEY,
    pilot_id               UUID          NOT NULL REFERENCES pilot(id),
    aircraft_id            UUID          NOT NULL REFERENCES aircraft(id),
    flight_track_id        UUID          REFERENCES flight_track(id),
    date                   DATE          NOT NULL,
    departure_place        VARCHAR(10)   NOT NULL,
    departure_time         TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_place          VARCHAR(10)   NOT NULL,
    arrival_time           TIMESTAMP WITH TIME ZONE NOT NULL,
    pic_name               VARCHAR(255)  NOT NULL,
    single_engine_minutes  INT NOT NULL DEFAULT 0,
    multi_engine_minutes   INT NOT NULL DEFAULT 0,
    total_minutes          INT NOT NULL,
    night_minutes          INT NOT NULL DEFAULT 0,
    ifr_minutes            INT NOT NULL DEFAULT 0,
    cross_country_minutes  INT NOT NULL DEFAULT 0,
    pic_minutes            INT NOT NULL DEFAULT 0,
    co_pilot_minutes       INT NOT NULL DEFAULT 0,
    dual_minutes           INT NOT NULL DEFAULT 0,
    instructor_minutes     INT NOT NULL DEFAULT 0,
    day_landings           INT NOT NULL DEFAULT 0,
    night_landings         INT NOT NULL DEFAULT 0,
    remarks                VARCHAR(2000),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flight_entry_pilot_id_date ON flight_entry(pilot_id, date);

CREATE TABLE simulator_session (
    id         UUID          PRIMARY KEY,
    pilot_id   UUID          NOT NULL REFERENCES pilot(id),
    date       DATE          NOT NULL,
    fstd_type  VARCHAR(100)  NOT NULL,
    minutes    INT           NOT NULL
);

CREATE INDEX idx_simulator_session_pilot_id_date ON simulator_session(pilot_id, date);
