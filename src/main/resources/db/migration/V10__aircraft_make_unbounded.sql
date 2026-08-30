-- Sanity-checked the real OpenSky CSV (94.5MB, ~520k rows) locally before it broke production a
-- third time: manufacturername has at least one row that's a 120-character joined list of surnames
-- (a data-entry error from an OpenSky contributor, not a real manufacturer) - past make's
-- VARCHAR(100) cap. registration (max real length 19) and model (max real length 74) stay
-- comfortably within their existing limits, so only make needs this - same reasoning as
-- V9__aircraft_reference_columns_unbounded.sql for why TEXT rather than a wider guessed cap.
--
-- aircraft_make_idx (from V7) has to go first - H2 (used for jOOQ codegen and the test suite's
-- simulated schema, see CLAUDE.md's Testing notes) refuses to index a CLOB/TEXT column at all,
-- and it was never buying much anyway: a plain btree index only accelerates prefix matches, not
-- the arbitrary-substring LIKE '%x%' the search endpoint actually does (V7's own migration
-- comment already noted this) - the model column keeps its own index, still VARCHAR(100).
DROP INDEX aircraft_make_idx;

ALTER TABLE aircraft ALTER COLUMN make TYPE TEXT;
