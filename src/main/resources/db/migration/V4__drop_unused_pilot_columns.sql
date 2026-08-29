-- Follow-up to V3 (see docs/plans/pilot-account-split.md): pilot.email/disabled_at were left in
-- place there as an expand-only step, with dropping them called out as later, separate work once
-- nothing reads them. No code in this repo has read or written either column since V3 landed, and
-- there's no real pilot/flight data deployed yet, so there's no live-deploy compatibility window to
-- protect - safe to drop directly rather than waiting for a deploy-cycle confirmation window.

ALTER TABLE pilot DROP COLUMN email;
ALTER TABLE pilot DROP COLUMN disabled_at;
