package com.bonney.hobbs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Thin orchestration shell over FlightEntryRepository/AircraftRepository - no business logic of
 * its own yet, same "thin service, rich domain" shape as things' Games/Pilots. Deriving a draft
 * entry from a FlightTrack (landing detection, night-time-from-sunset-tables, etc.) is future work
 * and deliberately not here - see CLAUDE.md.
 */
public class Logbook {

    private final FlightEntryRepository flightEntryRepository;
    private final AircraftRepository aircraftRepository;

    public Logbook(FlightEntryRepository flightEntryRepository, AircraftRepository aircraftRepository) {
        this.flightEntryRepository = flightEntryRepository;
        this.aircraftRepository = aircraftRepository;
    }

    public FlightEntry createEntry(PilotId pilotId, AircraftId aircraftId, FlightTrackId flightTrackId,
                                    LocalDate date, String departurePlace, OffsetDateTime departureTime,
                                    String arrivalPlace, OffsetDateTime arrivalTime, String picName,
                                    int singleEngineMinutes, int multiEngineMinutes, int totalMinutes,
                                    int nightMinutes, int ifrMinutes, int crossCountryMinutes,
                                    int picMinutes, int coPilotMinutes, int dualMinutes, int instructorMinutes,
                                    int dayLandings, int nightLandings, String remarks) {
        FlightEntry entry = new FlightEntry(FlightEntryId.random(), pilotId, aircraftId, flightTrackId, date,
                departurePlace, departureTime, arrivalPlace, arrivalTime, picName, singleEngineMinutes,
                multiEngineMinutes, totalMinutes, nightMinutes, ifrMinutes, crossCountryMinutes, picMinutes,
                coPilotMinutes, dualMinutes, instructorMinutes, dayLandings, nightLandings, remarks);
        flightEntryRepository.save(entry);
        return entry;
    }

    public Optional<FlightEntry> get(FlightEntryId id) {
        return flightEntryRepository.findById(id);
    }

    public List<FlightEntry> listForPilot(PilotId pilotId) {
        return flightEntryRepository.findAllByPilotId(pilotId);
    }

    public Aircraft createAircraft(String registration, String make, String model, EngineCategory engineCategory) {
        Aircraft aircraft = new Aircraft(AircraftId.random(), registration, make, model, engineCategory);
        aircraftRepository.save(aircraft);
        return aircraft;
    }

    public List<Aircraft> listAircraft() {
        return aircraftRepository.findAll();
    }
}
