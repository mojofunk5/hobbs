# Glossary

Terms used across `hobbs`'s code, docs, and PRs - split into standard aviation/CAA terminology
(defined by the CAP804/FCL.050 logbook format this app mirrors, not invented here) and this app's
own vocabulary. Alphabetical within each section.

## Aviation / CAA terms (CAP804 / FCL.050)

- **Co-pilot** - a second pilot on the flight who wasn't PIC (e.g. the non-flying pilot in a
  multi-crew aircraft). Recorded today as a bare duration (`coPilotMinutes`) with no identity
  attached - see [`FlightEntry.coPilotMinutes`](../src/main/java/com/bonney/hobbs/domain/FlightEntry.java).
- **Cross-country** - time flown on a flight that meets the regulatory distance-from-departure
  threshold to count toward cross-country experience requirements.
- **Dual** - time flown with an instructor on board, logged as `dualMinutes`.
- **FSTD** - Flight Simulation Training Device - the regulatory term for what a "simulator" actually
  is. `SimulatorSession` is `hobbs`'s row for FSTD time, kept separate from `FlightEntry` because the
  real logbook has its own row shape for it.
- **IFR** - Instrument Flight Rules - time flown by reference to instruments rather than visually.
- **Instructor** - time flown while acting as instructor, logged as `instructorMinutes` (distinct
  from `dualMinutes`, which is the *student's* side of the same dual flight).
- **Night** - time flown at night, per the regulatory definition (not just "after dark") - logged as
  `nightMinutes`, with `nightLandings` tracked separately from `dayLandings`.
- **PIC** - Pilot in Command - whoever was legally responsible for and in control of the aircraft on
  that specific flight. Not necessarily whose logbook the entry belongs to: a student's dual-training
  entry is owned by the student (`FlightEntry.pilotId`) but the instructor was PIC that flight, so
  `FlightEntry.pilotInCommandId` (a `PilotId` - see [`docs/plans/done/logbook-entries.md`](plans/done/logbook-entries.md))
  records the instructor, and `pilotInCommandMinutes` records how much of the flight the *entry's
  owner* spent as PIC (zero, on a fully dual flight). In code, spell out `pilotInCommand` rather than
  abbreviating to `pic` - this includes renaming the pre-existing `picMinutes` field, not just the
  new identity fields, so `pic*` doesn't survive anywhere as an inconsistent leftover.
- **Total time** - the whole flight duration, `totalMinutes`. Every other duration field
  (single/multi-engine, night, IFR, cross-country, PIC, co-pilot, dual, instructor) is a subset or
  breakdown of this, not an addition to it.

## `hobbs`-specific terms

- **Account** - the login/email/enabled-state half of a `Pilot`, one-to-one via `pilot_id`. A `Pilot`
  has an account iff a matching `Account` row exists. See
  [`docs/plans/done/pilot-account-split.md`](plans/done/pilot-account-split.md).
- **Draft entry** - a `FlightEntry` pre-filled from a `FlightTrack` for the pilot to confirm or
  correct, rather than typing everything by hand. Not yet built - see `CLAUDE.md` "Open work".
- **FlightEntry** - one row of the logbook. See the aviation terms above for its individual fields.
- **FlightTrack** - a raw GPS recording, stored as a single JSON blob, that can pre-fill a draft
  `FlightEntry`. Optional and never required - `flightTrackId` is nullable, and a manually-entered
  entry is exactly as valid as a GPS-derived one.
- **Known pilot** - a `Pilot` visible to a given caller in the pilot picker: themselves, anyone they
  created (`created_by`), or anyone they've logged a flight with as PIC or co-pilot. Privacy-scoped
  deliberately - the pilot table as a whole is never listable by a non-admin. See
  [`docs/plans/done/pilot-picker.md`](plans/done/pilot-picker.md).
- **Pilot** - someone recordable on a flight (as the entry's owner, as PIC, or - once built - as
  co-pilot); just `id`/`name`/`created_by`. Not the same as an **Account** - a `Pilot` can exist
  without one (see **Unclaimed pilot**).
- **Referral code** - an admin-issued, single-use, email-scoped, TTL-bound code required to register.
  Also used to let an **unclaimed pilot** claim their own account (`referral_code.claims_pilot_id`).
- **TypedId** - the base class for every domain identifier (`PilotId`, `AircraftId`,
  `FlightEntryId`, `FlightTrackId`, `SimulatorSessionId`) - a thin wrapper around `UUID` so domain
  boundaries never pass raw `UUID`s around untyped.
- **Unclaimed pilot** - a `Pilot` row with no matching `Account` - e.g. a co-pilot logged by name
  before they'd signed up themselves. Claimable later via a referral code scoped to that specific
  `PilotId`.
