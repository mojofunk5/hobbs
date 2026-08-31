# Plan: Picking a pilot you've flown with

**Status:** Designed 2026-08-30, fully implemented. Chunk 1 (backend,
[hobbs#9](https://github.com/mojofunk5/hobbs/pull/9)) 2026-08-30; chunk 2 (`hobbs-ui` picker
widget, [hobbs-ui#13](https://github.com/mojofunk5/hobbs-ui/pull/13)) 2026-08-30.

## Context

Story 1 of 3 split out of the "pasted-in ids when adding a flight entry" backlog item (see
`CLAUDE.md`'s Open work) - the other two (aircraft, location) are separate plans with different
scoping questions, not part of this doc.

Today `pilotInCommandId`/`coPilotId` on `POST /flight` take a raw `PilotId` the caller has to already
know and paste in (see [`docs/plans/logbook-entries.md`](logbook-entries.md)'s "no pilot search
endpoint yet" note). The only existing pilot listing is `GET /admin/pilots` - admin-gated, and
deliberately returns every pilot in the system (email, disabled state, sign-up/last-login) for admin
oversight. That's the wrong shape to build a picker against: a non-admin picker must not expose the
whole pilot table to every pilot.

Andy's framing: pilots should be private by default. If William logs a flight with Louis, only
William should see Louis as someone he's flown with - not every other pilot in the system. Claiming
Louis's record (inviting him to take ownership via `POST /pilot/{pilotId}/invite`) is a deliberately
separate, narrower mechanism that already exists and is unaffected by this plan.

## Confirmed decisions

- **New concept: a pilot "known to" a caller.** A `Pilot` X is known to caller C if any of:
  - X is C itself (you can always pick yourself, e.g. as PIC on a solo flight)
  - X.`createdBy` = C (C created X, whether or not a flight's been logged with them yet - covers the
    "create new pilot inline, then immediately select them" flow in the same session)
  - C has a `FlightEntry` (`FlightEntry.pilotId` = C, i.e. C's own logbook) where X is
    `pilotInCommandId` or `coPilotId`
- **Scoped to the authenticated caller only** - no `pilotId` request param, no admin override. Admin
  oversight keeps using the existing `GET /admin/pilots`, entirely unaffected by this plan.
- **Response carries `id`/`name` only** - reuse `PilotSummaryDto`, same shape `POST /pilot` already
  returns. No email, no account state - a picker has no business seeing either, and this matches
  `PilotSummaryDto`'s existing shape (unlike admin's `PilotListRow`, which does carry email/disabled
  for oversight purposes).
- **No pagination** - same reasoning as `GET /flight` and the logbook-entries plan generally: no real
  user has enough flight history yet for a "known to me" set to be large. Revisit if that changes.
- **Optional `search` query param**, substring match on name, case-insensitive. Omitted or empty
  returns the full known-set (bounded, per above) so `hobbs-ui` can show it as an initial dropdown
  before the pilot types anything.

## Design

### New endpoint

`GET /pilot?search=<optional>` - authenticated, not admin-gated. Returns `PilotSummaryDto[]`, sorted
by name.

```
GET /pilot                  -> everyone known to the caller
GET /pilot?search=lou       -> everyone known to the caller whose name contains "lou" (case-insensitive)
```

Distinct from `GET /admin/pilots` (unchanged) and from the existing `POST /pilot`/`PUT /pilot/{id}`/
`DELETE /pilot/{id}`/`POST /pilot/{id}/invite` (unchanged) - this is a new read added to
`PilotEndpoint`'s existing route group.

### Query

New `PilotRepository.findKnownTo(PilotId callerId, String search)`, mirroring
`findAllActivePage`'s existing style (jOOQ, `PILOT`/`FLIGHT_ENTRY` tables):

```sql
SELECT DISTINCT p.id, p.name
FROM pilot p
WHERE (
    p.id = :callerId
    OR p.created_by = :callerId
    OR p.id IN (SELECT pilot_in_command_id FROM flight_entry WHERE pilot_id = :callerId)
    OR p.id IN (SELECT co_pilot_id FROM flight_entry WHERE pilot_id = :callerId AND co_pilot_id IS NOT NULL)
)
AND (:search IS NULL OR p.name ILIKE '%' || :search || '%')
ORDER BY p.name
```

`Pilots.searchKnownTo(PilotId callerId, String search)` wraps the repository call, no new validation
needed (search is optional free text, not user-entered content that gets persisted).

### No migration

Pure read against existing columns (`pilot.created_by`, `flight_entry.pilot_id`/
`pilot_in_command_id`/`co_pilot_id`) - no schema change.

### `hobbs-ui`

- New `PilotApi.search(String query)` (mirrors `FlightApi`'s shape), hitting `GET /pilot?search=...`.
- A picker widget (typeahead: text field + filtered dropdown as you type) replacing the raw
  `pilotInCommandId`/`coPilotId` text fields on `CreateFlightEntryScreen`.
- When nothing matches: "Create new pilot" inline, calling the existing `POST /pilot` with the typed
  text as the name, then selecting the newly-created pilot for this entry - no new backend endpoint
  needed for this half, `POST /pilot` already does exactly this.
- Open UX question for that chunk (not a backend blocker): does the PIC field default-select "the
  caller themselves" for the common solo-flight case, or start empty? Andy's call when this chunk is
  actually built.

## Chunking

Per `CLAUDE.md`'s "keep PRs small" rule:

1. **Backend** (`hobbs`) - `findKnownTo` query, `Pilots.searchKnownTo`, `GET /pilot` route +
   OpenAPI doc, tests (own pilots, created-by pilots, flown-with pilots, self, search filter, and
   that another pilot's unrelated pilots are excluded).
2. **`hobbs-ui`** - `PilotApi.search`, the picker widget, wiring into `CreateFlightEntryScreen`'s
   PIC/co-pilot fields, "create new" inline flow.

## Explicitly out of scope (left for later)

- **Claiming a pilot record** (`POST /pilot/{pilotId}/invite`) - already exists, already scoped to
  the creator/admin, unaffected by and unrelated to this plan per Andy: claiming is a deliberately
  separate, narrower mechanism from just being able to *see* someone you've flown with.
- **Merging two `Pilot` records** - pre-existing backlog item (`CLAUDE.md` Open work), unaffected.
- **Aircraft and location pickers** - separate stories/plans, different scoping rules (aircraft is
  global; location doesn't have a domain entity yet at all).
