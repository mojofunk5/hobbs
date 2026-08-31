# Plan: `GET /airfield/recent` and `GET /aircraft/recent`

**Status:** Designed 2026-08-31, implemented 2026-08-31.

## Context

Investigating a `hobbs-ui` bug (departure/arrival airfield picker showing "No airfields found"
immediately on focus, before any typing) surfaced a design question worth fixing properly rather
than patching around: `AirfieldPicker` loads the **entire** ~1,200-row `airfield` table on focus
(`GET /airfield` with no `search`) purely to put the calling pilot's own last 5 flown airfields at
the front of a browsable dropdown - `Logbook.searchAirfields`'s recent-first splice, see its own
Javadoc. That's a lot of payload for "show me my last 5 airfields," and is the same code path the
`hobbs-ui` bug is showing symptoms in.

Compare the three pickers' on-focus behavior today:
- **Pilot:** `GET /pilot?search=` with no query returns the caller's full *privacy-scoped* known-pilot
  set (only people they've created or flown with, plus themselves - see
  `docs/plans/done/pilot-picker.md`). Genuinely small per pilot - a personal list, not a reference table.
  **No change needed here** - loading everything on focus is the right call at this scale.
- **Airfield:** loads the full ~1,200-row GB reference table on focus, just to surface 5 recent ones.
  **Redesigning this.**
- **Aircraft:** no on-focus load at all (requires typing 2+ characters) - reasonable given the
  ~600k-row scale, but it also means a pilot can't quickly repick "the aircraft I flew last" the way
  they can for pilot/airfield. **Adding this as a new capability**, not just a perf fix.

## Confirmed decisions

- **Add `GET /airfield/recent`**: returns the calling pilot's own last `RECENT_ITEMS_LIMIT` (5, see
  below) distinct flown airfields, most-recent first. Backed entirely by the *already-existing*
  `FlightEntryRepository#findRecentAirfieldIds` + `AirfieldRepository#findById` per id - `Logbook`
  already computes this list internally on every `searchAirfields` call today, this just exposes it
  directly instead of it only ever appearing spliced into a full-table/full-match response.
- **Add `GET /aircraft/recent`**: same shape, for aircraft. Needs a **new**
  `FlightEntryRepository#findRecentAircraftIds(PilotId, int limit)`, mirroring
  `findRecentAirfieldIds`'s exact pattern (walk `FLIGHT_ENTRY` newest-first by
  `date`/`departure_time`, collect distinct `aircraft_id` - a single column this time, not two -
  stop at `limit`).
- **`GET /airfield?search=` and `GET /aircraft?search=` are unchanged** - same params, same
  behavior, same recent-first ranking spliced into real search results. This is additive: `hobbs-ui`
  simply stops calling `GET /airfield?search=` with an empty query for the on-focus case.
  `GET /airfield?search=`'s documented "empty/missing search returns everything" contract stays
  exactly as-is for any other caller.
- **Neither new endpoint takes a `search` param** - caller identity from the session is the only
  input, since "recent" isn't a search. Same auth pattern as the other two endpoints
  (`SessionAuthFilter.AUTHENTICATED_PILOT_ID`).
- **Response shape matches the existing search endpoints** (`AirfieldDto[]`/`AircraftDto[]`) - the
  `hobbs-ui` picker widget doesn't need to know it's looking at a different endpoint's payload, only
  that it got a `List<Airfield>`/`List<Aircraft>` back.
- **Rename `Logbook.RECENT_AIRFIELD_LIMIT` (5) to `RECENT_ITEMS_LIMIT`** - it now backs two entities,
  not just airfield. Value stays 5.

## Expected result

- `AirfieldEndpoint`/`AircraftEndpoint` each gain one route + `@OpenApi` block.
- `Logbook` gains `recentAirfields(PilotId)` / `recentAircraft(PilotId)` - both thin (fetch ids via
  the repository, `findById` per id, drop any id whose airfield/aircraft has since been removed from
  the reference table, return in order).
- `FlightEntryRepository` gains `findRecentAircraftIds`, sibling to the existing
  `findRecentAirfieldIds`.
- No migration needed - no new columns or tables, this is a read-shape addition only.

## Explicitly out of scope

- Removing or changing `GET /airfield?search=`'s empty-search-returns-everything behavior - kept
  exactly as documented for compatibility, just no longer the mechanism `hobbs-ui`'s picker uses for
  on-focus browsing.
- Any change to `GET /pilot?search=` - out of scope, that dataset is small enough as-is (see
  Context).
- Caching/memoizing recent results - both new queries are cheap point-lookups against a single
  pilot's own (typically small) flight history, not a table scan; no perf concern to solve for.
