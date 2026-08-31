# Plan: Holder's Operating Capacity

**Status:** Scoped 2026-08-31, not yet implemented.

## Context

Comparing `FlightEntry` against a real CAP804/Pooleys logbook (William's) surfaced a gap: every row
in the physical book has a **Holder's Operating Capacity** column - a fixed CAA notation for what
role *the logbook's owner* played on that specific flight (`P1`, `P1/S`, `P2`, `P.u/t`, `N.1`, `N.2`,
`N.u/t`, `T.1`, `T.u/t`, `E.1`). `FlightEntry` has no field for this at all today.

This was initially confused with two things it isn't:
- It isn't "Captain" - that's a separate column in the physical book, already correctly modelled as
  `FlightEntry.pilotInCommandId` (a `Pilot` reference - see `docs/GLOSSARY.md`'s **PIC** entry). No
  change needed there.
- A student flying dual with an instructor is not a "co-pilot" (`P2` is a distinct CAA role for a
  second *qualified* pilot as a required crew member on multi-crew ops). A student's capacity is
  `P.u/t`, and their progress toward solo is already tracked via the existing `dualMinutes` field -
  see `docs/GLOSSARY.md`'s **Dual** entry. No new duration field needed for that either.

So this plan is scoped narrowly to just the one missing field.

## Domain shape after this plan

- New enum `HolderOperatingCapacity` (`domain` package). Constants are named for what they mean, not
  for the CAA shorthand, so the code reads sensibly without the logbook in hand - each constant
  carries the exact Pooleys/CAP804 notation text as data on the enum, not encoded anywhere else:

  | Enum constant                              | Notation | CAA meaning                                                                 |
  |---------------------------------------------|----------|------------------------------------------------------------------------------|
  | `PILOT_IN_COMMAND`                          | `P1`     | Pilot-in-Command                                                              |
  | `PILOT_IN_COMMAND_UNDER_SUPERVISION`        | `P1/S`   | Pilot-in-Command under supervision (each entry countersigned by the Captain) |
  | `SECOND_PILOT`                              | `P2`     | Second pilot exercising licence privileges as a required crew member         |
  | `PILOT_UNDER_TRAINING`                      | `P.u/t`  | Student pilot / pilot under training                                         |
  | `NAVIGATOR`                                 | `N.1`    | Navigator responsible for the aircraft's navigation                          |
  | `NAVIGATOR_UNDER_SUPERVISION`                | `N.2`    | Navigator acting under the supervision of the PIC                           |
  | `NAVIGATOR_UNDER_TRAINING`                   | `N.u/t`  | Navigator under training                                                     |
  | `RADIOTELEPHONY_OPERATOR`                    | `T.1`    | Licensed R/T operator responsible for all communications made               |
  | `RADIOTELEPHONY_OPERATOR_UNDER_TRAINING`     | `T.u/t`  | R/T operator under training                                                  |
  | `FLIGHT_ENGINEER`                            | `E.1`    | Flight engineer responsible for power units and auxiliary systems            |

  ```java
  public enum HolderOperatingCapacity {
      PILOT_IN_COMMAND("P1"),
      PILOT_IN_COMMAND_UNDER_SUPERVISION("P1/S"),
      SECOND_PILOT("P2"),
      PILOT_UNDER_TRAINING("P.u/t"),
      NAVIGATOR("N.1"),
      NAVIGATOR_UNDER_SUPERVISION("N.2"),
      NAVIGATOR_UNDER_TRAINING("N.u/t"),
      RADIOTELEPHONY_OPERATOR("T.1"),
      RADIOTELEPHONY_OPERATOR_UNDER_TRAINING("T.u/t"),
      FLIGHT_ENGINEER("E.1");

      private final String notation;

      HolderOperatingCapacity(String notation) {
          this.notation = notation;
      }

      public String getNotation() {
          return notation;
      }
  }
  ```

- `FlightEntry.holderOperatingCapacity` (`HolderOperatingCapacity`, required) - new field, new
  constructor parameter. Column `holder_operating_capacity` (`VARCHAR`, storing the enum name e.g.
  `PILOT_UNDER_TRAINING` - not the notation - so a future rename of the *notation* text alone,
  independent of the enum's identity, doesn't touch stored data).
- `CreateFlightEntryDto.holderOperatingCapacity` - the enum value only (e.g.
  `"PILOT_UNDER_TRAINING"`), Jackson-deserialized directly against the enum (same pattern as any
  other enum-typed DTO field in this codebase). The frontend never needs to know or send the
  notation - it only ever sends back a value it was previously given to render.
- `FlightEntryDto.holderOperatingCapacity` (the enum value) **and** a sibling
  `FlightEntryDto.holderOperatingCapacityNotation` (`String`) - both populated by
  `FlightEntryMapper` from the single domain enum (`entry.getHolderOperatingCapacity().name()` /
  `.getNotation()`). The notation field is derived at mapping time, never stored or accepted on
  write - this is the mechanism that keeps the CAA notation table out of `hobbs-ui` entirely: the
  backend is the only place that ever encodes "`P.u/t` means what," the frontend just displays
  whatever string it's given back.

## Out of scope

- No change to `pilotInCommandId`/`pilotInCommandMinutes`/`dualMinutes`/`coPilotId` - all already
  correctly shaped for what they represent (see Context above).
- No validation that the chosen capacity is consistent with other fields on the entry (e.g. flagging
  a `PILOT_IN_COMMAND` entry that also has non-zero `dualMinutes`). Worth revisiting once there's
  more than one real user's worth of data to see what mistakes actually happen.
- `hobbs-ui` rendering of the new field - separate repo, separate PR, follows once this backend
  contract lands.

## Chunking

### 1. Schema + domain (backend only, `hobbs`)
- Migration: add `holder_operating_capacity VARCHAR NOT NULL` to `flight_entry`. No real
  `FlightEntry` rows exist in production yet (same situation as the airfield-picker contract step -
  see `docs/DECISIONS.md`'s 2026-08-30 "chunk 6" entry), so this can be a straight `NOT NULL` add,
  not an expand/backfill/contract sequence.
- `HolderOperatingCapacity` enum, `FlightEntry.holderOperatingCapacity` field + constructor param,
  `FlightEntryRepository` read/write mapping.

### 2. DTOs + mapper + endpoint (backend only, `hobbs`)
- `CreateFlightEntryDto.holderOperatingCapacity`, `FlightEntryDto.holderOperatingCapacity` +
  `.holderOperatingCapacityNotation`, `FlightEntryMapper` wiring both directions.
- Update `docs/GLOSSARY.md`: add a **Holder's Operating Capacity** entry, and fix the stale
  **Co-pilot** entry while touching this area anyway - it currently says co-pilot is "recorded today
  as a bare duration with no identity attached," but `FlightEntry.coPilotId` already exists
  (added by `logbook-entries.md`, this glossary entry was never updated after).

### 3. `hobbs-ui` rendering (separate repo)
- Capacity picker on the entry form (sends the enum value only); read views render
  `holderOperatingCapacityNotation` rather than re-deriving it from the enum value client-side.
