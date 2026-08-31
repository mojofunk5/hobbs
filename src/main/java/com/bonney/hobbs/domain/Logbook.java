package com.bonney.hobbs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin orchestration shell over FlightEntryRepository/AircraftRepository - no business logic of
 * its own yet, deliberately: wiring belongs here, behaviour belongs on the domain objects
 * themselves. Deriving a draft entry from a FlightTrack (landing detection,
 * night-time-from-sunset-tables, etc.) is future work and deliberately not here - see CLAUDE.md.
 */
public class Logbook {

    /**
     * At least 2 characters, matching {@link AircraftRepository#search}'s own required-search
     * contract - the caller (the endpoint) must reject anything shorter before this is even
     * called, but this is the actual source of truth for the limit both check against.
     */
    public static final int MIN_AIRCRAFT_SEARCH_LENGTH = 2;
    private static final int MAX_AIRCRAFT_SEARCH_RESULTS = 50;

    /**
     * "Last N" distinct recently-flown items - both the airfields/aircraft surfaced first on
     * GET /airfield?search=/GET /aircraft?search= (ranking) and the full result of
     * GET /airfield/recent/GET /aircraft/recent (see docs/plans/picker-recent-endpoints.md).
     * Suggested value from docs/plans/airfield-picker.md's Confirmed decisions, flagged there as not
     * confirmed and easy to tune later since it's not a schema decision.
     */
    static final int RECENT_ITEMS_LIMIT = 5;

    private final FlightEntryRepository flightEntryRepository;
    private final AircraftRepository aircraftRepository;
    private final AirfieldRepository airfieldRepository;

    public Logbook(FlightEntryRepository flightEntryRepository, AircraftRepository aircraftRepository,
                    AirfieldRepository airfieldRepository) {
        this.flightEntryRepository = flightEntryRepository;
        this.aircraftRepository = aircraftRepository;
        this.airfieldRepository = airfieldRepository;
    }

    public FlightEntry createEntry(PilotId pilotId, AircraftId aircraftId, FlightTrackId flightTrackId,
                                    LocalDate date, OffsetDateTime departureTime, OffsetDateTime arrivalTime,
                                    AirfieldId departureAirfieldId, AirfieldId arrivalAirfieldId,
                                    PilotId pilotInCommandId, PilotId coPilotId,
                                    HolderOperatingCapacity holderOperatingCapacity, int singleEngineMinutes,
                                    int multiEngineMinutes, int totalMinutes, int nightMinutes, int ifrMinutes,
                                    int crossCountryMinutes, int pilotInCommandMinutes, int coPilotMinutes,
                                    int dualMinutes, int instructorMinutes, int dayLandings, int nightLandings,
                                    String remarks) {
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, flightTrackId, date,
                departureTime, arrivalTime, departureAirfieldId, arrivalAirfieldId,
                pilotInCommandId, coPilotId, holderOperatingCapacity, singleEngineMinutes, multiEngineMinutes,
                totalMinutes, nightMinutes, ifrMinutes, crossCountryMinutes, pilotInCommandMinutes, coPilotMinutes,
                dualMinutes, instructorMinutes, dayLandings, nightLandings, remarks);
        flightEntryRepository.save(entry);
        return entry;
    }

    public Optional<FlightEntry> get(FlightEntryId id) {
        return flightEntryRepository.findById(id);
    }

    public List<FlightEntry> listForPilot(PilotId pilotId) {
        return flightEntryRepository.findAllByPilotId(pilotId);
    }

    public List<Aircraft> searchAircraft(String search, boolean registrationOnly) {
        if (search == null || search.length() < MIN_AIRCRAFT_SEARCH_LENGTH) {
            throw new InvalidAircraftSearchException(MIN_AIRCRAFT_SEARCH_LENGTH);
        }
        return registrationOnly
                ? aircraftRepository.searchByRegistration(search, MAX_AIRCRAFT_SEARCH_RESULTS)
                : aircraftRepository.search(search, MAX_AIRCRAFT_SEARCH_RESULTS);
    }

    /**
     * Backs GET /airfield?search= - unlike {@link #searchAircraft}, an empty/missing search is
     * valid and returns the full GB set (alphabetical by name): ~1,200 rows is small enough that
     * this doesn't need aircraft's "must type 2+ characters" restriction (see
     * docs/plans/airfield-picker.md's Confirmed decisions).
     *
     * <p>Results are then re-ordered to put the calling pilot's own last {@link #RECENT_ITEMS_LIMIT}
     * distinct flown airfields first (most recently flown first, deduped via
     * {@link FlightEntryRepository#findRecentAirfieldIds}), everything else alphabetical after -
     * applies whether {@code search} is empty or not, so typing "S" still surfaces a recently-flown
     * Sherburn ahead of alphabetically-earlier matches. A recent airfield not present in the matched
     * set (e.g. filtered out by {@code search}) is simply skipped, never appended outside the match.
     */
    public List<Airfield> searchAirfields(PilotId callerId, String search) {
        List<Airfield> matches = search == null || search.isBlank()
                ? airfieldRepository.findAll()
                : airfieldRepository.search(search);

        List<AirfieldId> recentAirfieldIds = flightEntryRepository.findRecentAirfieldIds(callerId, RECENT_ITEMS_LIMIT);
        if (recentAirfieldIds.isEmpty()) {
            return matches;
        }

        Map<AirfieldId, Airfield> remaining = new LinkedHashMap<>();
        for (Airfield airfield : matches) {
            remaining.put(airfield.getId(), airfield);
        }

        List<Airfield> ordered = new ArrayList<>();
        for (AirfieldId recentId : recentAirfieldIds) {
            Airfield airfield = remaining.remove(recentId);
            if (airfield != null) {
                ordered.add(airfield);
            }
        }
        ordered.addAll(remaining.values());
        return ordered;
    }

    /**
     * Backs GET /airfield/recent (see docs/plans/picker-recent-endpoints.md) - the calling pilot's
     * own last {@link #RECENT_ITEMS_LIMIT} distinct flown airfields, most recently flown first. A
     * right-sized alternative to {@link #searchAirfields} with an empty search for callers (the
     * hobbs-ui airfield picker's on-focus load) that only want this, not the whole ~1,200-row table.
     * An id whose airfield has since been removed from the reference table is silently dropped.
     */
    public List<Airfield> recentAirfields(PilotId callerId) {
        return flightEntryRepository.findRecentAirfieldIds(callerId, RECENT_ITEMS_LIMIT).stream()
                .map(airfieldRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Backs GET /aircraft/recent (see docs/plans/picker-recent-endpoints.md) - the calling pilot's
     * own last {@link #RECENT_ITEMS_LIMIT} distinct flown aircraft, most recently flown first. Gives
     * the hobbs-ui aircraft picker an on-focus browse affordance it never had before, since
     * {@link #searchAircraft} has no empty-search "load everything" mode to reuse (aircraft's
     * ~600k-row scale rules that out). An id whose aircraft has since been removed from the
     * reference table is silently dropped.
     */
    public List<Aircraft> recentAircraft(PilotId callerId) {
        return flightEntryRepository.findRecentAircraftIds(callerId, RECENT_ITEMS_LIMIT).stream()
                .map(aircraftRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }
}
