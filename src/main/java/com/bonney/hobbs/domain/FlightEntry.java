package com.bonney.hobbs.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * One row of the CAA/EASA standard logbook (CAP804 / FCL.050 template). All durations are stored
 * in whole minutes rather than a float number of hours, to avoid rounding drift accumulating across
 * hundreds of entries - convert to "H:MM" only at the presentation edge.
 */
public class FlightEntry {

    private final FlightEntryId id;
    private final PilotId pilotId;
    private final AircraftId aircraftId;
    private final FlightTrackId flightTrackId;
    private final LocalDate date;
    private final String departurePlace;
    private final OffsetDateTime departureTime;
    private final String arrivalPlace;
    private final OffsetDateTime arrivalTime;
    private final PilotId pilotInCommandId;
    private final PilotId coPilotId;
    private final int singleEngineMinutes;
    private final int multiEngineMinutes;
    private final int totalMinutes;
    private final int nightMinutes;
    private final int ifrMinutes;
    private final int crossCountryMinutes;
    private final int pilotInCommandMinutes;
    private final int coPilotMinutes;
    private final int dualMinutes;
    private final int instructorMinutes;
    private final int dayLandings;
    private final int nightLandings;
    private final String remarks;

    public FlightEntry(FlightEntryId id, PilotId pilotId, AircraftId aircraftId, FlightTrackId flightTrackId,
                        LocalDate date, String departurePlace, OffsetDateTime departureTime,
                        String arrivalPlace, OffsetDateTime arrivalTime, PilotId pilotInCommandId,
                        PilotId coPilotId, int singleEngineMinutes, int multiEngineMinutes, int totalMinutes,
                        int nightMinutes, int ifrMinutes, int crossCountryMinutes,
                        int pilotInCommandMinutes, int coPilotMinutes, int dualMinutes, int instructorMinutes,
                        int dayLandings, int nightLandings, String remarks) {
        // FlightTrackId is deliberately nullable here - GPS recording is an optional fast-path onto
        // this same entry, never a requirement. A manually-entered flight has no track at all, and a
        // recording that cut out partway through still produces a valid entry with whatever the
        // track could pre-fill left as-is or corrected by hand.
        if (totalMinutes < 0) {
            throw new IllegalArgumentException("totalMinutes cannot be negative");
        }
        this.id = id;
        this.pilotId = pilotId;
        this.aircraftId = aircraftId;
        this.flightTrackId = flightTrackId;
        this.date = date;
        this.departurePlace = departurePlace;
        this.departureTime = departureTime;
        this.arrivalPlace = arrivalPlace;
        this.arrivalTime = arrivalTime;
        this.pilotInCommandId = pilotInCommandId;
        this.coPilotId = coPilotId;
        this.singleEngineMinutes = singleEngineMinutes;
        this.multiEngineMinutes = multiEngineMinutes;
        this.totalMinutes = totalMinutes;
        this.nightMinutes = nightMinutes;
        this.ifrMinutes = ifrMinutes;
        this.crossCountryMinutes = crossCountryMinutes;
        this.pilotInCommandMinutes = pilotInCommandMinutes;
        this.coPilotMinutes = coPilotMinutes;
        this.dualMinutes = dualMinutes;
        this.instructorMinutes = instructorMinutes;
        this.dayLandings = dayLandings;
        this.nightLandings = nightLandings;
        this.remarks = remarks;
    }

    public FlightEntryId getId() {
        return id;
    }

    public PilotId getPilotId() {
        return pilotId;
    }

    public AircraftId getAircraftId() {
        return aircraftId;
    }

    public Optional<FlightTrackId> getFlightTrackId() {
        return Optional.ofNullable(flightTrackId);
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDeparturePlace() {
        return departurePlace;
    }

    public OffsetDateTime getDepartureTime() {
        return departureTime;
    }

    public String getArrivalPlace() {
        return arrivalPlace;
    }

    public OffsetDateTime getArrivalTime() {
        return arrivalTime;
    }

    public PilotId getPilotInCommandId() {
        return pilotInCommandId;
    }

    public Optional<PilotId> getCoPilotId() {
        return Optional.ofNullable(coPilotId);
    }

    public int getSingleEngineMinutes() {
        return singleEngineMinutes;
    }

    public int getMultiEngineMinutes() {
        return multiEngineMinutes;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public int getNightMinutes() {
        return nightMinutes;
    }

    public int getIfrMinutes() {
        return ifrMinutes;
    }

    public int getCrossCountryMinutes() {
        return crossCountryMinutes;
    }

    public int getPilotInCommandMinutes() {
        return pilotInCommandMinutes;
    }

    public int getCoPilotMinutes() {
        return coPilotMinutes;
    }

    public int getDualMinutes() {
        return dualMinutes;
    }

    public int getInstructorMinutes() {
        return instructorMinutes;
    }

    public int getDayLandings() {
        return dayLandings;
    }

    public int getNightLandings() {
        return nightLandings;
    }

    public String getRemarks() {
        return remarks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FlightEntry that = (FlightEntry) o;
        return new EqualsBuilder().append(id, that.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("pilotId", pilotId)
                .append("date", date)
                .append("totalMinutes", totalMinutes)
                .toString();
    }
}
