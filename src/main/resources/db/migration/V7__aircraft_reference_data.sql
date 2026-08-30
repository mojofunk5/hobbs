-- Aircraft becomes reference data seeded from OpenSky's aircraftDatabase.csv rather than
-- pilot-submitted (see docs/plans/aircraft-picker.md) - these columns mirror the OpenSky CSV
-- fields so the import/reconciliation job (a later chunk of that plan) can upsert directly into
-- them. All new columns are nullable: existing rows (and any CSV rows missing a field) won't have
-- them, and there's no value that's genuinely "correct" to backfill instead of leaving unknown.
ALTER TABLE aircraft ADD COLUMN manufacturer_icao VARCHAR(10);
ALTER TABLE aircraft ADD COLUMN type_code VARCHAR(20);
ALTER TABLE aircraft ADD COLUMN serial_number VARCHAR(50);
ALTER TABLE aircraft ADD COLUMN operator VARCHAR(200);
ALTER TABLE aircraft ADD COLUMN owner VARCHAR(200);
ALTER TABLE aircraft ADD COLUMN built INTEGER;
ALTER TABLE aircraft ADD COLUMN engines VARCHAR(200);
ALTER TABLE aircraft ADD COLUMN category_description VARCHAR(200);

-- engine_category becomes derivable-but-not-always-known (from OpenSky's icaoaircrafttype engine
-- count digit, left null when it doesn't parse) rather than always pilot-supplied - see the plan
-- doc's "Existing aircraft table gets expanded" section.
ALTER TABLE aircraft ALTER COLUMN engine_category DROP NOT NULL;

-- Supports GET /aircraft?search= substring matching against what's now a ~600k-row table once the
-- full OpenSky import runs. Plain btree indexes only speed up prefix matches, not the
-- arbitrary-substring LIKE '%x%' the endpoint does - registration already has one via its unique
-- constraint. Full substring-search performance (e.g. Postgres pg_trgm) is deliberately left for
-- if/when that's actually observed to be slow against production-scale data, per
-- docs/DECISIONS.md's 2026-08-30 entry - the plan doc flags this as a reminder, not a resolved
-- design question.
CREATE INDEX aircraft_make_idx ON aircraft (make);
CREATE INDEX aircraft_model_idx ON aircraft (model);
