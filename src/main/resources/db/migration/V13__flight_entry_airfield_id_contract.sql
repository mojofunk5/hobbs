-- Contract step of FlightEntry.departurePlace/arrivalPlace -> AirfieldId migration (see V12's
-- expand step and docs/plans/airfield-picker.md). docs/DECISIONS.md's 2026-08-30 "chunk 4" entry
-- deliberately left no contract step scheduled, because there was no safe way to backfill
-- departure_airfield_id/arrival_airfield_id onto existing flight_entry rows that only ever had
-- free-text departure_place/arrival_place - dropping the free-text columns or making the id
-- columns NOT NULL would have stranded those rows.
--
-- That reasoning no longer applies: zero real flight_entry rows exist in production (confirmed
-- directly by the repo owner - the app is live but nobody has logged a real flight through it
-- yet). With no real data to lose or migrate, the owner explicitly decided to skip the staged
-- expand/backfill/contract sequence and go straight to the contract in one step now, rather than
-- carry the dual-field state indefinitely. See docs/DECISIONS.md's 2026-08-30 "airfield picker
-- chunk 6" entry for the full record of this override.
--
-- Residual risk worth naming: this is NOT a full exemption from CLAUDE.md's migration-safety rule
-- ("a migration must never break the code currently running against it"). That rule guards against
-- a *previous* release's code still running against the *new* schema - e.g. a partially-failed
-- deploy that leaves the old app container up after the migration has already run. "No real data"
-- removes the *data-loss* risk, but not that one: pre-this-PR code would still fail to INSERT a
-- flight_entry row once departure_airfield_id/arrival_airfield_id are required (it never sets
-- them), and would fail outright once departure_place/arrival_place no longer exist for it to
-- write to. This migration is only safe because it's deployed in lockstep with a matching
-- hobbs-ui release (a sibling PR in that repo) that already sends the new required fields and no
-- longer sends the old ones - migration and code moving together, same as the deploy pipeline's
-- normal `migrate` -> `docker compose up -d app` sequencing already assumes. Deliberately no
-- defensive code added here or in the application layer to tolerate the old shape - that would
-- defeat the point of contracting the schema.
ALTER TABLE flight_entry ALTER COLUMN departure_airfield_id SET NOT NULL;
ALTER TABLE flight_entry ALTER COLUMN arrival_airfield_id SET NOT NULL;
ALTER TABLE flight_entry DROP COLUMN departure_place;
ALTER TABLE flight_entry DROP COLUMN arrival_place;
