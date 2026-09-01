# Plan: Show Pooleys notation in the Holder's Operating Capacity picker

**Status:** Designed 2026-09-01, not yet implemented.

## Context

`docs/plans/done/holder-operating-capacity.md` (chunk 3) built `HolderOperatingCapacityField` in
`hobbs-ui` as a dropdown over a hardcoded list of the 10 enum values, each labelled with a
mechanical humanisation of the enum constant's own name (e.g. `PILOT_IN_COMMAND` → "Pilot In
command") rather than the CAA/Pooleys shorthand (`P1`, `P.u/t`, etc.). That plan's stated design
goal - keeping the CAA notation table itself out of `hobbs-ui` entirely, so the backend stays the
only place that encodes what each code means - got conflated during implementation with the
picker's on-screen *labels* too. That was never the original ask: Andy specified the frontend
should display Pooleys' own shorthand, specifically so a pilot transcribing an entry back into a
physical CAP804 logbook sees the exact code printed in that logbook, not a paraphrase of the enum
name.

Read views already get this right - `FlightEntry.holderOperatingCapacityNotation` is
server-derived per entry and `ViewFlightEntryScreen` renders it as-is. Only the picker is wrong.

Hardcoding a second copy of the notation table into `hobbs-ui` would fix the label but reintroduce
exactly the duplication the original plan wanted to avoid - two places would encode "`P.u/t` means
what," and they could drift. Instead, this follows the same pattern the other four
create-flight-entry pickers already use: `GET /flight-entry-context`
(`docs/plans/done/new-entry-context-endpoint.md`) prefetches everything those pickers need in one
call the create screen already makes on load. Capacity becomes a fifth field on that same
aggregate response instead of a hardcoded client-side list.

## Confirmed decisions

- **New field on `FlightEntryContextDto`: `holderOperatingCapacities`** - a
  `HolderOperatingCapacityDto[]`, one entry per `HolderOperatingCapacity` enum constant, in enum
  declaration order (the same order the picker should list them in, matching how the physical
  logbook groups them: `P1`, `P1/S`, `P2`, `P.u/t`, `N.1`, `N.2`, `N.u/t`, `T.1`, `T.u/t`, `E.1`).
- **New small DTO, `HolderOperatingCapacityDto`** - `value` (`String`, the enum's own `.name()`)
  and `notation` (`String`, `.getNotation()`). Reuses the exact value/notation pairing
  `FlightEntryDto` already exposes per-entry; no new backend concept, just the full static list
  instead of one entry's row.
- **`FlightEntryContextEndpoint` gains one more mapped list** -
  `HolderOperatingCapacity.values()` mapped to the new DTO, alongside the existing three lookups.
  Still pure aggregation, no new domain/repository method - `values()` is free, unlike the other
  three fields which each call a real `Logbook`/`Pilots` method.
- **`hobbs-ui`'s `FlightEntryContext` model gains `holderOperatingCapacities`** - a new
  `HolderOperatingCapacity` model class (`{value, notation}`) mirroring the DTO.
  `FlightEntryContextApi.fetch` parses it the same way as the other three lists.
- **`HolderOperatingCapacityField` stops hardcoding `_holderOperatingCapacityValues` and the
  `_humanize` helper.** It instead takes the prefetched list as a constructor parameter - the same
  `initialSuggestions`-shaped pattern `AircraftPicker`/`AirfieldPicker`/`PilotPicker` already use -
  and renders each `DropdownMenuItem`'s visible label as that entry's `notation`. No humanized text
  remains.
- **`CreateFlightEntryScreen` passes `_context?.holderOperatingCapacities` down**, same as it
  already does for the other four pickers' `initialSuggestions`.
- **The dropdown item's underlying *value* is unchanged** - still the raw wire enum value (e.g.
  `PILOT_IN_COMMAND`). Only the visible label text changes, from humanized name to notation.

## Expected result

- `hobbs`: `HolderOperatingCapacityDto` (new file), `FlightEntryContextDto` gains a fourth field,
  `FlightEntryContextEndpoint` adds one more mapped list, `HobbsClient` test client's
  `flightEntryContext()` call unaffected in shape (additive field only).
- `hobbs-ui`: `HolderOperatingCapacity` model (new file, `{value, notation}`), `FlightEntryContext`
  gains the field, `FlightEntryContextApi` parses it, `HolderOperatingCapacityField` rewritten to
  consume a passed-in list and render notation labels, `CreateFlightEntryScreen` wires
  `_context?.holderOperatingCapacities` through.
- No migration, no new endpoint, no new round trip on either side - same call the screen already
  makes, one more field in the response it already gets back.

## Explicitly out of scope

- **How the picker sends data.** Still the raw enum value - unchanged.
- **`ViewFlightEntryScreen`.** Already correct (renders the per-entry
  `holderOperatingCapacityNotation` field, unaffected by this plan).
- **`GET /flight` (list) or `FlightEntryDto`.** This only touches the context aggregate endpoint.
- **An edit/amend screen.** Doesn't exist yet (see `hobbs`'s `CLAUDE.md` "Open work") - nothing to
  wire up there.

## Chunking

Small enough that each repo's change is a single PR, same split as the original
`holder-operating-capacity.md` plan's chunk 2 → chunk 3:

1. **`hobbs`** - `HolderOperatingCapacityDto`, `FlightEntryContextDto`/`FlightEntryContextEndpoint`
   change, test coverage.
2. **`hobbs-ui`** - model/API/widget/screen changes, once chunk 1 is merged and deployed.
