-- Pilots are never deleted - only accounts are (disable/enable/delete all act on Account, not
-- Pilot; see docs/plans/pilot-account-split.md). A Pilot record persists permanently as the
-- enduring "person recordable on a flight" identity so logged flight history stays attributed to
-- the same PilotId even after the account behind it is removed - deleting an account just drops
-- back to the same "unclaimed" state as a Pilot that was never registered. pilot.deleted_at is
-- therefore dead weight; dropped directly since there's no real pilot/flight data deployed yet.

ALTER TABLE pilot DROP COLUMN deleted_at;
