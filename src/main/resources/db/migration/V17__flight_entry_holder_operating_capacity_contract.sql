-- Contract step: every row has been backfilled by V15, so holder_operating_capacity can now be
-- required (see docs/plans/holder-operating-capacity.md). Per CLAUDE.md's migration-safety rule,
-- this is only safe deployed in lockstep with the app release that starts writing this column on
-- every insert (this same PR) - the previous release's code doesn't set it, so it must not still be
-- running against this schema once this migration has applied.
ALTER TABLE flight_entry ALTER COLUMN holder_operating_capacity SET NOT NULL;
