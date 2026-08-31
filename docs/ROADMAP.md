# Roadmap

A living high-level view of what's shipped, what's in flight, and what's next, across **both**
`hobbs` and [`hobbs-ui`](https://github.com/mojofunk5/hobbs-ui). Each `docs/plans/*.md` doc (in
either repo) carries the detailed design for one item; this file is the index and the sequencing -
it exists because "what's the state of everything" otherwise means cross-referencing every plan
doc's `Status:` line by hand, which drifts out of date exactly the way this file's own 2026-08-31
sweep found six of them had (see the note at the bottom of this file).

## Shipped (chronological)

1. **Scaffold** - self-hosted PPL flight logbook backend (2026-08-24).
2. **Pilot/Account split** - `Pilot` (a person recordable on a flight) separated from `Account` (the
   login), enabling an *unclaimed* pilot (2026-08-29) -
   [`docs/plans/pilot-account-split.md`](plans/pilot-account-split.md).
3. **Logbook entries: add / view / list** - real `pilotInCommandId`/`coPilotId` on `FlightEntry`
   (backend) plus `hobbs-ui`'s create/view/list screens (2026-08-29) -
   [`docs/plans/logbook-entries.md`](plans/logbook-entries.md).
4. **CI speedups** - parallel test execution, Docker layer/Gradle build caching, skip CI for
   docs-only commits (2026-08-29 to 2026-08-30, both repos).
5. **Integration test suite split by endpoint** - `HobbsApplicationIntegrationTest`'s ~950 lines
   split into six per-endpoint classes, 5 chunks (2026-08-30) -
   [`docs/plans/split-integration-test-by-endpoint.md`](plans/split-integration-test-by-endpoint.md).
6. **Aircraft reference data & picker** - seeded from OpenSky, all 4 chunks including the `hobbs-ui`
   widget and Browse Aircraft screen (2026-08-30) -
   [`docs/plans/aircraft-picker.md`](plans/aircraft-picker.md).
7. **Pilot picker** - privacy-scoped `GET /pilot?search=` plus the `hobbs-ui` widget (2026-08-30) -
   [`docs/plans/pilot-picker.md`](plans/pilot-picker.md).
8. **Airfield reference data & picker** - seeded from OurAirports, all 6 backend chunks, the
   `hobbs-ui` widget, and the `FlightEntry` airfield-id contract (departure/arrival place is now a
   required `AirfieldId`, free text removed entirely) (2026-08-30) -
   [`docs/plans/airfield-picker.md`](plans/airfield-picker.md).
9. **Aircraft search trigram indexes** - Postgres `pg_trgm` GIN indexes fixing slow substring search
   at the aircraft table's real (~600k row) scale (2026-08-30, perf-only).
10. **Typeahead picker UX pass** - loading/finished/no-results/clear feedback made consistent across
    all three `hobbs-ui` pickers (2026-08-30) - see `hobbs-ui`'s `docs/architecture-brief.md`
    decisions.
11. **Picker recent-items endpoints** - `GET /airfield/recent`/`GET /aircraft/recent`, replacing
    "fetch the whole reference table on focus," plus `hobbs-ui`'s on-focus loading (2026-08-31) -
    [`docs/plans/picker-recent-endpoints.md`](plans/picker-recent-endpoints.md).
12. **`GET /flight-entry-context`** - one prefetch call aggregating what the create-flight-entry
    screen's four pickers need, plus `hobbs-ui` consuming it (2026-08-31) -
    [`docs/plans/new-entry-context-endpoint.md`](plans/new-entry-context-endpoint.md).
13. **Split `CreateFlightEntryScreen`** (`hobbs-ui`, 2026-08-30) - see that repo's
    `docs/plans/split-create-flight-entry-screen.md`.

## In flight - designed, not yet built

- **Holder's Operating Capacity** - [`docs/plans/holder-operating-capacity.md`](plans/holder-operating-capacity.md).
  Design merged 2026-08-31 ([hobbs#56](https://github.com/mojofunk5/hobbs/pull/56)); chunks 1-3
  (schema/domain, DTOs/mapper/glossary, `hobbs-ui` rendering) not started.
- **`FlightTrack` phase classification & `FlightEntry` derivation** -
  [`docs/plans/flight-track-derivation.md`](plans/flight-track-derivation.md) / `hobbs-ui`'s
  `docs/plans/flight-recording.md`. Design-only; depends on on-device recording, which itself
  depends on the iOS app existing (see Backlog below).
- **`GET /flight-track/{id}` map endpoint** -
  [`docs/plans/flight-track-map-endpoint.md`](plans/flight-track-map-endpoint.md) / `hobbs-ui`'s
  `docs/plans/flight-track-map.md`. Design-only; depends on GPS recording landing first.
- **Extract a shared `TypeaheadPicker<T>` widget** (`hobbs-ui`) - `docs/plans/typeahead-picker.md`.
  The on-focus-loading behaviour it was revised for is already shipped directly in the three
  hand-rolled pickers; the generic extraction itself isn't done.

## Backlog, in rough priority order

1. **Keep READMEs and architecture docs current, both repos.** *(added 2026-08-31)* A one-off sweep
   plus an ongoing discipline check: `hobbs/README.md` + `CLAUDE.md` and `hobbs-ui/README.md` +
   `docs/architecture-brief.md` should each reflect exactly what's actually shipped. This roadmap's
   own creation was prompted by finding six stale plan-doc `Status:` lines and a fully-obsolete
   `CLAUDE.md` bullet (the "pasted-in ids" item describing pilot/aircraft/airfield pickers as
   partially unmerged, when all three are done end-to-end) - proof that "update docs in the same PR
   as the change" doesn't self-enforce. Do an actual pass over both repos' README/architecture docs
   against reality, then re-check this roadmap against both repos' merged PRs periodically.
2. **Editing/deleting a flight entry.** Only create/view/list exist - no way to fix a mistyped entry
   or remove one yet.
3. **Photo-to-logbook OCR.** *(expanded 2026-08-31)* Take a photo of a paper logbook page and create
   one or more draft `FlightEntry` rows from it - reviewed/corrected before saving, never
   auto-committed, same precedent as a `FlightTrack`-derived draft entry. Concretely motivated by
   William's real Pooleys logbook (entirely handwritten - re-typing it by hand is the alternative
   this replaces); see [`docs/reference/pooleys-logbook-notation.jpg`](reference/pooleys-logbook-notation.jpg)
   and [`pooleys-logbook-rules.jpg`](reference/pooleys-logbook-rules.jpg) for the exact CAP804 column
   layout and notation an OCR pass needs to parse. Per-column mapping onto the domain: date; aircraft
   type/registration (resolve against the aircraft reference table, same as the picker); Captain name
   (resolve or create a `Pilot`, same as the pilot picker); Holder's Operating Capacity notation
   (parse against the `HolderOperatingCapacity` enum once
   [`docs/plans/holder-operating-capacity.md`](plans/holder-operating-capacity.md) ships - this is
   why that plan should land first); departure/arrival place and times (resolve against the airfield
   reference table); and the duration/landings columns. Depends on holder-operating-capacity (above)
   for a complete field mapping - could start before it lands but would need a follow-up pass to fill
   in that one column.
4. **Merging two `Pilot` records** - no way to reconcile a self-registered account with an unclaimed
   record someone else already created for the same person.
5. **`SimulatorSession`/`FlightTrack` referencing a co-pilot's `PilotId`** - `FlightEntry` has both
   now; the other two flight-domain classes don't yet.
6. **Pagination/filtering on `GET /flight`** - currently returns everything for the authenticated
   pilot.
7. **The iOS app** (`hobbs-ui`) - web-only today; iOS (and eventually Android) come from the same
   Flutter codebase.
8. **GPS-recording-to-logbook** (`hobbs-ui`, depends on #7) - start a recording, derive a draft
   `FlightEntry` from the track on completion. The MVP-completing feature; needs a real mobile
   platform for background location, not a web tab.

## Keeping this file honest

- Add a line to "Shipped" in the same PR that merges the last chunk of an item.
- Move an item from "Backlog" to "In flight" once its plan doc is designed and merged; move it to
  "Shipped" once its last chunk merges - and, per `CLAUDE.md`'s "Closing out a plan doc" rule, move
  the plan doc itself into `docs/plans/done/` in that same PR, updating any link to it.
- This file, `CLAUDE.md`'s "Open work" section, `hobbs-ui`'s `docs/architecture-brief.md` "Open work
  / roadmap" section, and both repos' README "Not yet built"/"What's here today" sections should
  never disagree with each other or with reality - if you update one, check the others in the same
  change.
