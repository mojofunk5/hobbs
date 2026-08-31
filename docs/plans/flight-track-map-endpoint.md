# Plan: read endpoint for rendering a `FlightTrack` on a map

**Status:** Designed 2026-08-31, not yet implemented. Depends on GPS recording actually landing
first (see "Depends on" below) - this doc is reviewable/mergeable now, implementation isn't
scheduled yet. Companion doc:
[`hobbs-ui`'s `docs/plans/flight-track-map.md`](https://github.com/mojofunk5/hobbs-ui/blob/master/docs/plans/flight-track-map.md)
covers the client-side map widget this endpoint feeds.

## Context

`FlightTrack`'s class Javadoc has said since it was written that the stored JSON blob is "only ever
read back as a unit (to derive a draft `FlightEntry`, **or to redraw the route on a map**)" - the
map use case was anticipated from day one but nothing serves it yet. `docs/plans/
flight-track-derivation.md` (merged) covers the first half - deriving a draft `FlightEntry` from a
track - via `POST /flight-track/{id}/derive`. That endpoint returns derived logbook fields, not the
raw point list, and derivation is a one-shot POST anyway, not something a view screen calls
repeatedly. Showing a pilot the actual flown route on `ViewFlightEntryScreen` needs a separate, plain
read endpoint over the same `FlightTrack` the derivation endpoint already reads.

## Confirmed decisions

- **New endpoint, `GET /flight-track/{id}`.** Read-only, returns the track's points for the caller to
  render - not a resource pilots list or search, so no collection endpoint alongside it.
- **Authorization: caller must be the track's owning pilot.** `FlightTrack.pilotId` already exists
  for exactly this - same ownership check shape as the existing `GET /flight-entry/{id}` (a pilot
  can't fetch someone else's data by guessing an id). 403 (not 404) on a mismatch, matching the
  existing convention for owned-resource lookups elsewhere in `endpoint/`.
- **Response is downsampled server-side, not the raw stored points verbatim.** A 2+ hour flight at
  the adaptive sampling rate `docs/plans/flight-recording.md` describes (dense near takeoff/landing,
  sparser at cruise) can be several thousand points - fine for the phase classifier, which needs
  full fidelity, but wasteful to ship to a map widget that's just drawing a route line. Apply the
  Ramer-Douglas-Peucker algorithm (well-known, no new dependency needed - a straightforward
  ~40-line implementation over lat/lon pairs) with a fixed tolerance tuned to stay visually
  identical to the raw track at typical map zoom levels. Simplification only ever drops points
  that don't change the visible line shape - it's a rendering optimisation, not a data loss the
  pilot could be misled by.
- **New response DTO, `FlightTrackDto`**, with `id`, `startedAt`, `endedAt` (nullable, mirrors
  `FlightTrack.getEndedAt()`), and `points` (`FlightTrackPointDto[]`: `lat`, `lon`, `alt` - no
  timestamp per point, since the map widget only draws a static polyline, not a time-scrubbable
  replay; see "Explicitly out of scope"). Barometric/accelerometer fields from
  `flight-track-derivation.md`'s point-schema addition are deliberately not included here - they're
  classifier inputs, meaningless to a map polyline.
- **No new domain logic beyond the simplification step.** The handler loads the `FlightTrack` via
  the existing `FlightTrackRepository`, checks ownership, parses `pointsJson`, runs
  Ramer-Douglas-Peucker, and maps to `FlightTrackDto`. No new repository query.

## Expected result

- `FlightTrackEndpoint` (new file) registers `GET /flight-track/{id}`, OpenAPI-annotated.
- `FlightTrackDto`/`FlightTrackPointDto` (new files) as described above.
- A small `PolylineSimplifier` (or similarly named) utility implementing Ramer-Douglas-Peucker,
  unit-tested directly against hand-built point-list fixtures (straight lines, simple curves) -
  ordinary domain-adjacent utility testing, not a new capability this doc needs to call out.
- `HobbsClient` (the Feign-style test client) gains a matching `flightTrack(id)` method.
- No migration - read-shape addition only, same as `docs/plans/done/new-entry-context-endpoint.md`.

## Depends on

- **GPS recording and derivation actually being implemented** (`docs/plans/flight-recording.md` /
  `docs/plans/flight-track-derivation.md`, both designed, neither built yet - gated on the iOS app
  existing per `docs/architecture-brief.md`'s roadmap). There's no real `FlightTrack` data to render
  until that lands. This doc is written and reviewable now so the design isn't lost, but
  implementation shouldn't be scheduled ahead of its dependency.

## Explicitly out of scope

- **Time-scrubbable replay (playing the flight back over time) or altitude/speed graphs alongside
  the map.** Both plausible future features once raw track data is actually flowing, but nobody has
  asked for them yet - this doc only covers a static route-on-a-map view. Adding per-point
  timestamps to `FlightTrackPointDto` later, if replay gets built, is additive and doesn't need this
  endpoint's shape reconsidered.
- **An aeronautical chart tile layer (airspace/airfields) behind the route.** Purely a client-side
  map-widget concern (which tile source to render under the polyline) - doesn't touch this endpoint
  at all. See the companion `hobbs-ui` doc.
- **Caching the simplified response.** Same reasoning as other read-endpoint plans in this repo - a
  single point-list transform over one pilot's own track, not a table scan, no perf concern to solve
  for.
- **The `hobbs-ui` map widget itself, tile provider choice, and screen wiring.** Separate doc/PR in
  `hobbs-ui` - see the companion doc linked above.
