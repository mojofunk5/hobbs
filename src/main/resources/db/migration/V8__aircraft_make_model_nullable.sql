-- Real OpenSky rows exist with a registration and engine/type data but no manufacturer name or
-- model - e.g. an entry keyed off icao24/typecode with the manufacturername/model fields blank.
-- V7 correctly relaxed engine_category for this reason but missed make/model, which crashed the
-- import job on the first real-world row that hit this (a hand-written test fixture had no reason
-- to include a row like that). registration stays the one field the import job actually requires
-- - a row without it is skipped before it's ever inserted (see AircraftImportJob) - this only
-- relaxes the two fields OpenSky itself sometimes leaves blank.
ALTER TABLE aircraft ALTER COLUMN make DROP NOT NULL;
ALTER TABLE aircraft ALTER COLUMN model DROP NOT NULL;
