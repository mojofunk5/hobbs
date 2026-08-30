-- New Airfield reference table, seeded from OurAirports' GB dataset (see
-- docs/plans/airfield-picker.md) - same "self-owned reference data imported from an external
-- source" pattern as V7__aircraft_reference_data.sql. No behaviour change here: FlightEntry still
-- uses free-text departurePlace/arrivalPlace (see docs/plans/airfield-picker.md's chunk 4 for that
-- migration, sequenced separately since it touches existing rows).
--
-- name/municipality are bounded VARCHARs rather than TEXT, unlike aircraft's reference columns
-- (V9/V10) - those were widened only after real OpenSky data broke a guessed cap; OurAirports
-- names/municipalities are structurally short (real airport/place names), so there's no known
-- failure mode to design around pre-emptively here. Keeping them bounded also means name can be
-- indexed - H2 (used for jOOQ codegen and the test suite, see CLAUDE.md's Testing notes) refuses to
-- index a CLOB/TEXT column at all, which is exactly what tripped up V10's aircraft_make_idx.
CREATE TABLE airfield (
    id             UUID             PRIMARY KEY,
    icao_code      VARCHAR(10),
    name           VARCHAR(200)     NOT NULL,
    municipality   VARCHAR(200),
    iso_country    VARCHAR(5)       NOT NULL,
    iso_region     VARCHAR(10)      NOT NULL,
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    elevation_ft   INTEGER,
    type           VARCHAR(30)      NOT NULL,
    -- sourceName/sourceId are the upsert key for the re-runnable import job (e.g. "ourairports" +
    -- their own row id) - internal to the domain entity only, never exposed on AirfieldDto (see
    -- docs/plans/airfield-picker.md's Confirmed decisions).
    source_name    VARCHAR(30)      NOT NULL,
    source_id      VARCHAR(30)      NOT NULL,
    CONSTRAINT airfield_source_unique UNIQUE (source_name, source_id)
);

-- Backs GET /airfield?search= - case-insensitive substring match on name, exact/prefix match on
-- icao_code (see docs/plans/airfield-picker.md's Open questions, flagged there as needed in the
-- same migration that creates the table, same reminder as the aircraft-picker plan's own migration
-- chunk). Plain btree indexes only actually accelerate the icao_code prefix match, not name's
-- arbitrary-substring LIKE '%x%' - same caveat as aircraft_make_idx/aircraft_model_idx in
-- V7__aircraft_reference_data.sql - but ~1,200 rows makes this a non-issue in practice either way.
CREATE INDEX airfield_icao_code_idx ON airfield (icao_code);
CREATE INDEX airfield_name_idx ON airfield (name);
