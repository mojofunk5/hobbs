-- Expand step of adding FlightEntry.holderOperatingCapacity (see docs/plans/holder-operating-capacity.md).
-- Unlike the airfield-picker id columns (V12), existing flight_entry rows here are Andy's own real
-- (if made-up) test data, not a clean slate - so this follows CLAUDE.md's standard
-- expand -> backfill -> contract sequence rather than going straight to NOT NULL. Nullable for now;
-- V16 backfills every existing row, V17 makes the column required.
ALTER TABLE flight_entry ADD COLUMN holder_operating_capacity VARCHAR;
