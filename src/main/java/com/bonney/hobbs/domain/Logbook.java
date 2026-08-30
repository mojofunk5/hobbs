package com.bonney.hobbs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
                                    LocalDate date, String departurePlace, OffsetDateTime departureTime,
                                    String arrivalPlace, OffsetDateTime arrivalTime, PilotId pilotInCommandId,
                                    PilotId coPilotId, int singleEngineMinutes, int multiEngineMinutes,
                                    int totalMinutes, int nightMinutes, int ifrMinutes, int crossCountryMinutes,
                                    int pilotInCommandMinutes, int coPilotMinutes, int dualMinutes,
                                    int instructorMinutes, int dayLandings, int nightLandings, String remarks) {
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, flightTrackId, date,
                departurePlace, departureTime, arrivalPlace, arrivalTime, pilotInCommandId, coPilotId,
                singleEngineMinutes, multiEngineMinutes, totalMinutes, nightMinutes, ifrMinutes,
                crossCountryMinutes, pilotInCommandMinutes, coPilotMinutes, dualMinutes, instructorMinutes,
                dayLandings, nightLandings, remarks);
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
     */
    public List<Airfield> searchAirfields(String search) {
        return search == null || search.isBlank()
                ? airfieldRepository.findAll()
                : airfieldRepository.search(search);
    }
}
