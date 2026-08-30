-- Expand step of FlightEntry.departurePlace/arrivalPlace -> AirfieldId migration (see
-- docs/plans/airfield-picker.md's Open questions and docs/DECISIONS.md's 2026-08-30 entry for the
-- full expand/backfill/contract sequencing). Adds new nullable id columns referencing airfield(id)
-- alongside the existing departure_place/arrival_place free-text columns - those are untouched, no
-- rename, no NOT NULL, no data migration in this step.
--
-- Deliberately NO backfill here and none planned: there is no reliable way to match an existing
-- row's free-text place string (e.g. "EGCM", "Sherburn") to a specific airfield.name/icao_code
-- without unreliable fuzzy string-matching, which docs/plans/airfield-picker.md's own reasoning
-- (and feedback_ids_over_text) explicitly argues against. Existing rows simply keep
-- departure_airfield_id/arrival_airfield_id NULL - consistent with CLAUDE.md's migration-safety
-- rule that the *previous* release's code (which only ever wrote the free-text columns) must keep
-- producing correct rows against this new schema, which nullable expand-only columns guarantee.
--
-- No contract step (dropping departure_place/arrival_place, or making the id columns NOT NULL) is
-- included or currently planned - removing the free-text columns is itself a breaking schema/API
-- change that needs its own review, tracked as a follow-up in CLAUDE.md's Open work rather than
-- done here.
ALTER TABLE flight_entry ADD COLUMN departure_airfield_id UUID REFERENCES airfield(id);
ALTER TABLE flight_entry ADD COLUMN arrival_airfield_id UUID REFERENCES airfield(id);
