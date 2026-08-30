# Plan: Airfield reference data & picker

**Status:** Designed 2026-08-30. Not yet implemented.

## Context

Third of the three "pasted-in ids when adding a flight entry" stories (see `CLAUDE.md`'s Open work)
- pilot and aircraft are done ([`docs/plans/pilot-picker.md`](pilot-picker.md),
[`docs/plans/aircraft-picker.md`](aircraft-picker.md)). This is the "picking or registering a
location" story flagged there as needing a domain decision first: `FlightEntry.departurePlace`/
`arrivalPlace` are still plain `String`s (see `FlightEntry.java`), not an id referencing a real
entity.

Confirmed direction: a self-owned `Airfield` reference table, seeded from a public dataset, carrying
coordinates - same shape as the aircraft picker (reference data imported from an external source,
picker searches it, no pilot-facing "add" flow), not a live maps API dependency. Coordinates are
wanted anyway for the not-yet-built night-time-from-sunset-tables work in FlightTrack -> FlightEntry
derivation (`CLAUDE.md` Open work), so this isn't scope beyond what's already planned to need it.

## Data source: OurAirports

Checked what's actually available before designing against it:

- The **UK CAA's AIP** is authoritative but published as PDF/eAIP charts, not a structured dataset,
  and only covers licensed aerodromes - Sherburn-in-Elmet and most small GA strips fall outside that
  scope anyway.
- **OurAirports** (`https://davidmegginson.github.io/ourairports-data/airports.csv`) is free,
  unauthenticated, global, and includes small/unlicensed strips. Confirmed by pulling the live file:
  85,996 rows worldwide, 1,605 for `iso_country = GB` (968 `small_airport`, 71 `medium_airport`, 17
  `large_airport`, plus 382 `closed`, 160 heliports, 5 seaplane bases, 2 balloonports). Sherburn is in
  there: `EGCJ`, `Sherburn-in-Elmet Airfield`, lat/lon populated. Columns: `id`, `ident`, `type`,
  `name`, `latitude_deg`, `longitude_deg`, `elevation_ft`, `continent`, `iso_country`, `iso_region`,
  `municipality`, `scheduled_service`, `icao_code`, `iata_code`, `gps_code`, `local_code`,
  `home_link`, `wikipedia_link`, `keywords`. Crowdsourced/community-maintained - no uptime/accuracy
  guarantee, but it's the closest thing to a standard in the GA app world and is what other tools in
  this space seed from.

## Confirmed decisions

- **`Airfield` becomes reference data, not pilot-submitted** - same pattern as `Aircraft`. No
  free-text/"add an airfield" affordance in the picker yet. Deliberately deferred rather than
  designed now: William currently flies out of a single airfield (Sherburn-in-Elmet), so there's no
  live gap to fill, and it's the same kind of premature scope the aircraft-picker plan's "Out of
  scope" section already reasoned about (build a gap-filling mechanism once a real gap shows up, not
  ahead of it).
- **Import scope: GB only, active airfields only.** Unlike the aircraft picker (deliberately global,
  since an aircraft could be flown anywhere), this imports `iso_country = GB` rows only, and drops
  `type = closed`. Revisit to widen if/when William flies abroad - noted in Out of scope, not
  designed now.
- **Airfield types kept: `small_airport`/`medium_airport`/`large_airport` only.** Heliports,
  seaplane bases, and balloonports are dropped at import - not relevant to a fixed-wing PPL logbook.
- **Import is a re-runnable job**, modelled the same way as `import-aircraft`: a new CLI subcommand
  (e.g. `HobbsApplication import-airfields`), idempotent upsert. Upsert key is `(sourceName,
  sourceId)` - OurAirports' own row `id`, not `icaoCode`, since a handful of small strips in the
  dataset have no ICAO code at all and `id` is the only field guaranteed present and stable across
  re-imports.
- **`Airfield` fields:**

  ```
  Airfield - id, icaoCode (nullable), name, municipality, isoCountry, isoRegion,
             latitude, longitude, elevationFt, type, sourceName, sourceId
  ```

  - `icaoCode` nullable - some small strips in the dataset genuinely don't have one.
  - `isoCountry`/`isoRegion` kept even though every row is `GB` today - cheap to carry, and avoids
    baking a UK-only assumption into the schema for what's likely a temporary import-scope decision
    (see above). `isoRegion` also gives a natural grouping (e.g. `GB-ENG`) if the picker ever wants
    it.
  - `type` kept from the source data for potential sort/relevance use in the picker later.
  - `sourceName` (e.g. `"ourairports"`) + `sourceId` (their row `id`) are the upsert key for
    re-imports. **Internal to the domain entity only - never added to `AirfieldDto`.** Same
    endpoint -> mapper -> dto -> domain layering as the rest of the domain; worth a one-line comment
    on these two fields in the entity noting they're import-only, since "don't leak this externally"
    isn't obvious from the field names alone.
  - Dropped from the source data as not logbook-relevant: `continent`, `scheduled_service`,
    `iata_code`, `gps_code` (redundant with `icaoCode` for essentially all GB rows), `home_link`,
    `wikipedia_link`, `keywords`.
- **Picker searches by name or ICAO code - no pre-filled default.** Confirmed 2026-08-30 (revised
  from an earlier "single default dropdown" draft): a lookup a pilot can actually type - "Sherburn"
  or "EGCJ" - is the real requirement, not a default selection. `GET /airfield?search=` does a
  case-insensitive substring match against `name` and an exact/prefix match against `icaoCode`
  (matches OurAirports' own `ident`/`gps_code`/`local_code` convention of using the ICAO code as the
  primary identifier), combined in one query rather than two separate fields in the UI. Given ~1,200
  active GB rows - small enough that an empty `search` can reasonably return the full set (same shape
  as the pilot picker's empty-search behaviour, unlike aircraft's "must type 2+ characters"
  restriction driven by its ~600k-row scale). **No airfield is pre-filled or pre-selected in the
  `hobbs-ui` picker, including Sherburn-in-Elmet** - Andy's explicit call (2026-08-30): William is
  being used as a guide for what to build first, but Hobbs itself isn't a William-only logbook, and a
  hardcoded default would bake that assumption into the UI for every pilot who ever uses it.

## Open questions (for review on this doc, before implementation)

- **Index needed on `airfield.icao_code`/`name`** to back the search endpoint - same reminder as the
  aircraft-picker plan's migration chunk, worth adding in the same migration that creates the table
  rather than as an afterthought, even though ~1,200 rows won't show a missing-index problem in
  practice for a while.
- **`FlightEntry.departurePlace`/`arrivalPlace` migration path.** Changing these from `String` to an
  `AirfieldId` reference is a breaking schema change for any existing rows with free-text place
  names (there's real test/seed data with plain strings today per `CLAUDE.md`'s migration-safety
  rule) - needs its own expand/backfill/contract sequence, not a single migration. Scoping that
  sequence is implementation work, not a decision this doc needs to make, but flagging it now so the
  chunking below accounts for it.

## Chunking

Per `CLAUDE.md`'s "keep PRs small" rule:

1. **Migration** - new `airfield` table (see Design above). No behaviour change; `FlightEntry` still
   uses free-text places.
2. **Import job** - CSV parsing filtered to `iso_country = GB`, non-closed, airport-type rows,
   upsert-by-`(sourceName, sourceId)` logic, new CLI subcommand, tests against a fixture CSV (not a
   live network call in tests).
3. **`GET /airfield?search=` endpoint** + OpenAPI doc - name substring / ICAO code match, empty
   search returns the full ~1,200-row GB set, per Confirmed decisions above.
4. **`FlightEntry.departurePlace`/`arrivalPlace` -> `AirfieldId` migration** - expand/backfill/
   contract sequence per the open question above. Likely its own sub-plan given the data-safety
   constraint, not a single PR.
5. **`hobbs-ui`** - picker widget (search-as-you-type by name/code, no pre-filled default).

## Explicitly out of scope (left for later)

- **Free-text "add an airfield" fallback.** Not designed - see Confirmed decisions above. Revisit
  once William (or a future pilot) actually flies somewhere not in the GB seed set.
- **Non-GB airfields.** Import is GB-only for now; widen to global (or a specific additional country)
  once there's a real need, same reasoning as the GB-only scoping decision above.
- **Sunset/sunrise-table night-time derivation that would consume `Airfield` coordinates.** Tracked
  separately under FlightTrack -> FlightEntry derivation in `CLAUDE.md`'s Open work; this plan only
  makes the coordinates available, doesn't build the consumer.
