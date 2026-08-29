# Plan: Logbook entries (add / view / list)

**Status:** Scoped 2026-08-29, not yet implemented.

## Context

`hobbs-ui`'s roadmap (`hobbs-ui/docs/architecture-brief.md`) lists "logbook screens" as the next
item now that the [Pilot/Account split](pilot-account-split.md) is done. That split was the
prerequisite: it made an *unclaimed* `Pilot` possible, so someone recordable on a flight no longer
has to already have an account.

The backend (`hobbs`) already has `POST /flight`, `GET /flight`, and `GET /flight/{id}` -
see `FlightEntryEndpoint.java`. What's missing:
- `FlightEntry.picName` is free text, and `coPilotMinutes` is a bare duration with no identity -
  both predate the split and were deliberately left alone by it (see that plan's "explicitly out of
  scope"). Now that `Pilot`/`PilotId` exists as the right domain object for "a person", both should
  reference it instead of (or in addition to) text - see [[feedback_ids_over_text]].
- `hobbs-ui` has no flight-entry screens or API client at all yet - greenfield.

Tracked here in `hobbs` rather than `hobbs-ui` because this plan spans both repos' work end to end;
see `docs/DECISIONS.md` for where the actual code review/merge history lives per repo.

**No real flight/pilot data is deployed yet** (same situation the pilot/account split was in) - so:
- The schema change in chunk 1 can be a straight column swap, not an expand/contract migration.
- Chunks 3/4 (view/list) can lean on the entry's id directly rather than building navigation-first
  UI - if you know the id, `GET /flight/{id}` is a fine way to view it; a "my entries" list isn't a
  precondition for viewing one.

## Domain shape after this plan

Naming: spell out `pilotInCommand` rather than abbreviating to `pic` in code/schema/DTOs - `pic*`
stays only where it's already an established field name for a *duration* (`picMinutes`), not for
the new identity fields this plan adds.

- `FlightEntry.pilotInCommandId` (`PilotId`, required) replaces `picName` (`String`) - who was PIC
  on this specific flight. Distinct from `FlightEntry.pilotId` (whose logbook the entry belongs to)
  - see `docs/GLOSSARY.md`'s **PIC** entry for why those two can differ (e.g. a student's dual
  entry).
- `FlightEntry.coPilotId` (`Optional<PilotId>`, nullable) - new. A flight can be solo (no co-pilot),
  so this stays optional, same nullability pattern as `flightTrackId`. `coPilotMinutes` (the
  duration) is unaffected - it's a separate concept from *who* the co-pilot was.
- Column names follow suit: `pilot_in_command_id` (not `pic_id`), `co_pilot_id`.
- Both `pilotInCommandId` and `coPilotId` must already exist as `Pilot` rows before an entry references them.
  `POST /pilot` (added by the account split, for creating an *unclaimed* pilot) already covers
  creating a `Pilot` record for someone with no account yet - no new "create pilot inline" logic
  needed in `FlightEntryEndpoint`.
- No pilot search/autocomplete endpoint exists yet (`GET /admin/pilots` is admin-only). Given no
  real users, chunk 2's UI can take a `PilotId` directly (created via `POST /pilot` first, or an
  existing one pasted in) rather than building a search-by-name picker - that picker is a fair
  thing to add later once there's more than one pilot's worth of data to search over.

## Chunking

Each chunk is its own PR per `CLAUDE.md`'s "keep PRs small" rule.

### 1. The id change (backend only, `hobbs`)
- Migration: drop `pic_name`, add `pilot_in_command_id UUID NOT NULL REFERENCES pilot(id)` and
  `co_pilot_id UUID NULL REFERENCES pilot(id)` on `flight_entry`. Straight swap (no
  expand/contract - no live data to protect), same reasoning as `V4`/`V5` in the account split.
- `FlightEntry.java`: `picName` (String) → `pilotInCommandId` (PilotId); add `coPilotId`
  (`Optional<PilotId>`, same pattern as `getFlightTrackId()`).
- `FlightEntryRepository`, `FlightEntryMapper`, `FlightEntryDto`, `CreateFlightEntryDto`,
  `Logbook.createEntry(...)`: thread both ids through. jOOQ codegen needs re-running against the
  new columns.
- Update `docs/GLOSSARY.md`'s PIC entry and `CLAUDE.md`/README's field lists to match (already
  drafted assuming this change lands).
- No `hobbs-ui` work - nothing there references `picName` yet.

### 2. Adding an entry (mostly `hobbs-ui`)
- Backend: `POST /flight` already exists and already takes every field except the two new ids,
  which chunk 1 adds - likely no backend change needed here beyond what chunk 1 already did.
- `hobbs-ui`: new `flight_api.dart` (mirrors `auth_api.dart`'s shape) plus a create-entry screen -
  the CAP804/FCL.050 form fields, `picId`/`coPilotId` as plain text-entered `PilotId`s for now (see
  "no search picker yet" above), `aircraftId` likewise until an aircraft picker exists.

### 3. Viewing a single entry (mostly `hobbs-ui`)
- Backend: `GET /flight/{id}` already exists, already 403s a non-owner - no change needed.
- `hobbs-ui`: a view screen reachable by pasting/navigating to a known entry id directly (per the
  "no users yet" note above) - doesn't need to be reached via a list first.

### 4. Listing entries (mostly `hobbs-ui`)
- Backend: `GET /flight` already exists (returns everything for the authenticated pilot, no
  pagination - see `CLAUDE.md`'s existing "Not yet built" note on that) - no change needed.
- `hobbs-ui`: a list screen, each row navigating to chunk 3's view screen by id.

## Explicitly out of scope (left for later)

- Pilot search/autocomplete - see "no pilot search endpoint" above.
- Aircraft picker in the create-entry screen - same "type the id for now" reasoning.
- Editing/deleting an existing entry - only add/view/list are scoped here.
- Pagination/filtering on `GET /flight`.
- Deriving a draft entry from a `FlightTrack`, and referencing a co-pilot's `PilotId` from
  `SimulatorSession`/`FlightTrack` - both remain separate, later plans per `CLAUDE.md`.
