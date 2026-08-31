# Plan: `GET /flight-entry-context` - a single prefetch call for starting a new entry

**Status:** Designed 2026-08-31, not yet implemented.

## Context

`docs/plans/picker-recent-endpoints.md` made `hobbs-ui`'s pickers lazy: each of `AirfieldPicker`
(departure/arrival), `AircraftPicker`, and `PilotPicker` (PIC/co-pilot) fetches its own suggestion
list the moment it gains focus, via `GET /airfield/recent`, `GET /aircraft/recent`, and
`GET /pilot?search=` (no query) respectively. That's the right call for a screen a pilot might only
partly interact with - but the create-flight-entry screen isn't that screen: `aircraftId`,
`departureAirfieldId`, `arrivalAirfieldId`, and `pilotInCommandId` are all **required** fields on
`FlightEntry` (see its class Javadoc; only `coPilotId`/`flightTrackId` are nullable), so a pilot
creating a new entry is essentially certain to focus all four pickers before they can save. Lazy
loading buys nothing there and costs something real on a poor connection: each focus is its own
round trip, so a pilot working through the form on a weak signal waits once per field instead of
once for the whole screen.

Discussed alongside a UI-side question (how should `hobbs-ui` consume a prefetched batch instead of
each picker doing its own fetch) - that half is out of scope here, see "Explicitly out of scope"
below.

## Confirmed decisions

- **New endpoint, `GET /flight-entry-context`.** Takes no query params - caller identity from the
  session is the only input, same pattern as `GET /airfield/recent`/`GET /aircraft/recent`. Returns
  everything the create-flight-entry screen's pickers need in one response:
  - `recentAirfields`: same data and shape as `GET /airfield/recent` (`AirfieldDto[]`, capped at
    `Logbook.RECENT_ITEMS_LIMIT`)
  - `recentAircraft`: same as `GET /aircraft/recent` (`AircraftDto[]`, same cap)
  - `knownPilots`: same data and shape as `GET /pilot?search=` with no query (`PilotSummaryDto[]`,
    uncapped - see `docs/plans/pilot-picker.md`)
- **Pure aggregation, no new domain logic.** The handler calls exactly the three existing methods -
  `Logbook.recentAirfields(callerId)`, `Logbook.recentAircraft(callerId)`,
  `Pilots.searchKnownTo(callerId, null)` - and maps each result with the existing
  `AirfieldMapper`/`AircraftMapper`/`PilotSummaryDto` construction already used by the endpoints
  being composed. No new repository query, no new `Logbook`/`Pilots` method.
- **New response DTO, `FlightEntryContextDto`**, with exactly those three list fields
  (`AirfieldDto[]`, `AircraftDto[]`, `PilotSummaryDto[]`). Reuses the three existing element DTOs
  verbatim rather than inventing a parallel shape for each.
- **New endpoint class, `FlightEntryContextEndpoint`**, its own file rather than folded into
  `FlightEntryEndpoint`/`AirfieldEndpoint`/`AircraftEndpoint`/`PilotEndpoint` - mirrors the
  admin-endpoint precedent (`docs/DECISIONS.md`'s 2026-08-30 "Admin stays its own endpoint/`/admin/*`
  namespace" entry): a concern that spans multiple resources gets its own endpoint rather than being
  awkwardly owned by one of them. It's also the first endpoint that needs both `Logbook` and `Pilots`
  injected.
- **The three individual endpoints are unchanged and stay.** This is additive, not a replacement -
  see "Explicitly out of scope" for why they're still needed.
- **`hobbs-ui` calls this once**, in the create-flight-entry screen's `initState`, not per-picker-
  focus - the actual behaviour change motivating this plan. Wiring the pickers to consume a
  prefetched batch instead of each doing its own on-focus fetch is real UI-side design work, and is
  explicitly out of scope for this doc (see below) - this doc only commits to the backend contract
  the UI work will consume.

## Expected result

- `FlightEntryContextEndpoint` (new file) registers `GET /flight-entry-context`, injected with both
  `Logbook` and `Pilots`.
- `FlightEntryContextDto` (new file) - three-field aggregate DTO as above.
- `HobbsClient` (the Feign-style test client) gains a matching `flightEntryContext()` method.
- No migration - no new columns or tables, this is a read-shape addition only, same as
  `picker-recent-endpoints.md`.

## Explicitly out of scope

- **Removing or deprecating `GET /airfield/recent`, `GET /aircraft/recent`, or
  `GET /pilot?search=`.** Kept for: a picker re-fetching after being cleared and refocused (the
  batch snapshot from screen load goes stale the moment the pilot picks something and clears it
  again - see `AirfieldPicker`/`AircraftPicker`'s existing clear-and-reload behaviour in
  `docs/plans/picker-recent-endpoints.md`), and for any future screen that doesn't want a full
  prefetch (see the edit/amend point below).
- **Extending this pattern to editing/amending a flight entry.** Not yet built (see `hobbs`'s
  `CLAUDE.md` "Open work"). Whether prefetch-everything is still the right call there is a genuinely
  open question - amending an entry likely means correcting one or two fields, not touching all four
  required pickers, so the "they're all required, so they'll all get focused" argument this plan
  leans on doesn't obviously transfer. Revisit once that screen exists rather than guessing now.
- **The `hobbs-ui` wiring itself.** How the three pickers stop doing their own on-focus fetch and
  instead accept a prefetched suggestion list is UI-side widget API design (an
  `initialSuggestions`-shaped parameter, roughly) - a separate doc + PR in `hobbs-ui`, mirroring how
  `docs/plans/picker-recent-endpoints.md` (this repo) and `docs/plans/typeahead-picker.md`
  (`hobbs-ui`) were split last time.
- **Caching or memoizing the composite response.** Same reasoning as
  `docs/plans/picker-recent-endpoints.md`'s equivalent line: three cheap point-queries against one
  pilot's own small history, not a table scan - no perf concern to solve for.
- **Any change to `Pilots.searchKnownTo`'s uncapped behaviour.** Out of scope, unchanged from
  `docs/plans/pilot-picker.md`.
