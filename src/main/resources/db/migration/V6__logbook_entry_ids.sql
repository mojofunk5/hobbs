-- Follow-up to the pilot/account split (see docs/plans/logbook-entries.md): pic_name was left as
-- free text there because an unclaimed Pilot record wasn't possible yet. It is now, so pic_name
-- becomes a real reference to a pilot(id) row, and a co-pilot gets one too (previously untracked
-- beyond a bare duration). No real flight/pilot data is deployed yet, so this is a straight
-- swap/rename rather than an expand/contract migration - same reasoning as V4/V5.

ALTER TABLE flight_entry DROP COLUMN pic_name;
ALTER TABLE flight_entry ADD COLUMN pilot_in_command_id UUID NOT NULL REFERENCES pilot(id);
ALTER TABLE flight_entry ADD COLUMN co_pilot_id UUID REFERENCES pilot(id);
ALTER TABLE flight_entry RENAME COLUMN pic_minutes TO pilot_in_command_minutes;
