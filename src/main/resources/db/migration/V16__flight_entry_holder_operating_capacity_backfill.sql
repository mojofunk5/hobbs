-- Backfill step for V15's expand (see docs/plans/holder-operating-capacity.md). Every existing row
-- is Andy's own test data - there's no capacity that's actually "correct" for a made-up flight - but
-- it must be set to one of the real HolderOperatingCapacity enum constants (PILOT_IN_COMMAND, the
-- most common real-world case) rather than a placeholder, so the column never holds a value the
-- enum itself couldn't produce going forward.
UPDATE flight_entry SET holder_operating_capacity = 'PILOT_IN_COMMAND' WHERE holder_operating_capacity IS NULL;
