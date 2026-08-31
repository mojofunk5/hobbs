# Plan: `FlightTrack` phase classification and `FlightEntry` derivation

**Status:** Designed 2026-08-31, not yet implemented. Companion doc:
[`hobbs-ui`'s `docs/plans/flight-recording.md`](https://github.com/mojofunk5/hobbs-ui/blob/main/docs/plans/flight-recording.md)
covers the on-device recording half.

## Context

`CLAUDE.md`'s "Open work" has listed **FlightTrack → FlightEntry derivation** since the MVP was
scoped (2026-08-24): parsing a raw GPS recording into a draft `FlightEntry` (departure/arrival
place+time, landing counts including touch-and-goes, night time, cross-country distance). This doc
is that design. It was prompted by thinking through what William - flying circuits and
touch-and-goes at Sherburn while working toward his PPL - actually needs from this feature: an
accurate landing/touch-and-go count matters to him specifically, more than it would for someone
logging one takeoff and one landing per flight.

Two questions had to be settled before this could be built: how much of the classification logic
runs on-device vs. server-side, and whether it's the same state machine in both places.

## Confirmed decisions

- **The backend holds the one authoritative phase-classification state machine. The client's is a
  separate, deliberately simpler thing that only drives sampling rate - see the companion `hobbs-ui`
  doc.** Not "the same state machine in two languages": Dart and Java share no code, so keeping two
  full implementations in lockstep is a drift risk with no upside. `hobbs`'s classifier runs
  server-side, after the flight, over the complete uploaded track - it can look forward and backward
  across a window rather than reacting point-by-point, which makes it strictly better-positioned to
  get touch-and-goes right than anything guessing in real time on a phone. The client's local guess is
  disposable UX plumbing (live "Airborne"/"Taxiing" status on screen, and the sampling-rate decision);
  it is never written to `FlightEntry` and the two are never compared or reconciled.

- **`FlightTrack`'s stored JSON gains per-point sensor fields, additively.** Currently (per
  `docs/GLOSSARY.md`) a single JSON blob of raw GPS points. Each point gains optional
  `barometricAltitudeM` (from the device's pressure sensor, relative altitude - see rationale below)
  and `verticalAccelerationG` (peak accelerometer reading in the sample window, for touchdown
  detection) alongside the existing lat/lon/alt/timestamp. Both nullable at the point level - a point
  recorded on a device or OS without sensor access is still a valid point, same principle as
  `flightTrackId` being nullable at the `FlightEntry` level. No new columns; this is an addition to
  the existing JSON shape, not a schema migration.

- **Phase classification is a state machine over (ground speed, vertical speed, barometric rate of
  change), with hysteresis.** States: `stationary`, `taxiing`, `takeoffRoll`, `airborne`,
  `landingRoll`. Transitions are speed-threshold-based with a minimum dwell time before a transition
  is accepted (hysteresis) - avoids flapping between `taxiing`/`stationary` at a threshold boundary
  from GPS noise alone. Barometric rate of change is the primary signal for the
  `takeoffRoll`→`airborne` and `airborne`→`landingRoll` edges specifically (a pressure-altitude jump
  is a cleaner airborne/ground signal than GPS vertical speed, which is noisy near the ground); ground
  speed alone still classifies `stationary`/`taxiing`/`takeoffRoll`/`landingRoll` when barometric data
  isn't present on a point.

- **Touch-and-go vs. full-stop landing is a duration-and-reacceleration test on `landingRoll`, not a
  separate state.** A `landingRoll` segment that reaccelerates back through flying speed within a
  short window (few seconds, exact threshold tuned during implementation against real Sherburn
  circuit data once William flies one to record) is classified `touchAndGo`; one that doesn't -
  ground speed drops toward zero and stays there - is `fullStopLanding`. This is the crux of what
  William needs from the feature, per the Context section.

- **Takeoff/landing/touch-and-go counts fall out of the state machine's transitions, not a separate
  count pass.** Every `takeoffRoll`→`airborne` transition is a takeoff; every `landingRoll` segment is
  either a `touchAndGo` (contributes to touch-and-go count, and to CAA landing-currency count per
  `docs/GLOSSARY.md`'s landing terms) or a `fullStopLanding` (contributes to `dayLandings`/
  `nightLandings`, existing `FlightEntry` fields). Day/night split reuses whatever sunset/sunrise
  table decision `CLAUDE.md`'s "Open work" night-time bullet already calls for - not re-litigated
  here.

- **Departure/arrival airfield is derived by nearest-match against the existing `Airfield` reference
  table, not asked of the pilot up front.** The first `stationary`→`taxiing`→`takeoffRoll` segment's
  GPS position is matched against `Airfield` (the ~1,200-row GB reference table from
  `docs/plans/done/airfield-picker.md`, already seeded/searchable) by nearest distance; same for the final
  `landingRoll`/`fullStopLanding` segment's position for arrival. Reuses `AirfieldId` directly -
  `departureAirfieldId`/`arrivalAirfieldId` are already required, non-free-text fields on
  `FlightEntry` (per that same plan), so this derivation produces exactly the type the field already
  expects. No new geocoding dependency - it's a nearest-neighbour lookup against data already in the
  database.

- **The derivation output is always a draft, never auto-saved.** Consistent with the MVP framing in
  `CLAUDE.md`: auto-detection will sometimes get a touch-and-go count or an airfield match wrong
  (a go-around misclassified as a touch-and-go, a private strip not in the `Airfield` table matching
  to the wrong nearest entry), so the derived fields pre-fill the existing create-`FlightEntry` form
  for William to confirm or correct - same UX as a manually-started entry, just with the pickers and
  duration fields already populated. This doc defines the derivation; wiring it into the create-entry
  screen as a pre-fill is `hobbs-ui`-side work, out of scope here (see below).

## Expected result

- `FlightTrack`'s JSON point shape gains the two optional sensor fields described above.
- A new domain class, tentatively `FlightTrackPhaseClassifier` (name to be finalised at
  implementation time), takes a `FlightTrack` and returns an ordered list of phase segments
  (`stationary`/`taxiing`/`takeoffRoll`/`airborne`/`landingRoll`, with `landingRoll` segments further
  tagged `touchAndGo`/`fullStopLanding`) plus the derived departure/arrival `AirfieldId`s and
  timestamps.
- A new endpoint, tentatively `POST /flight-track/{id}/derive` (exact shape TBD at implementation
  time), returns a draft-`FlightEntry`-shaped DTO from the classifier's output for the client to
  pre-fill the create-entry form with - never writes a `FlightEntry` itself.
- `docs/GLOSSARY.md` gains entries for `touchAndGo`/`fullStopLanding` phase terms once implemented.

## Explicitly out of scope

- **Cross-country distance derivation.** A separate `CLAUDE.md` "Open work" bullet; not tackled here.
- **Night-time derivation (sunset/sunrise tables).** Same - existing separate open item, this doc's
  landing-count logic doesn't depend on it being solved first (day/night landing split can land as a
  follow-up once that exists).
- **Tuning the exact speed/duration thresholds.** Real values (what ground speed means "taxiing" vs.
  "takeoff roll", how many seconds of reacceleration distinguishes a touch-and-go from a full stop)
  need real track data to tune - William's first few recorded flights, not guessed up front. The
  implementation should keep these as named constants in one place, not scattered magic numbers, so
  they're cheap to retune after seeing real data.
- **The client-side real-time classifier, background recording, local storage, and adaptive GPS
  sampling.** All `hobbs-ui`-side - see the companion doc.
- **Wiring the derived draft into the create-flight-entry screen's pre-fill.** `hobbs-ui`-side UI
  work, follows from `POST /flight-track/{id}/derive` existing but is a separate doc/PR there.
- **A car-based test mode or fixture data for validating the classifier without a real flight.**
  Belongs in the companion `hobbs-ui` doc (that's where the recording/test-mode UX lives), though the
  backend classifier should be unit-testable directly against a hand-built `FlightTrack` fixture
  either way - ordinary domain-class testing, not a new capability this doc needs to call out.
