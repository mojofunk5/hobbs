package com.bonney.hobbs.domain;

/**
 * The CAP804/FCL.050 "Holder's Operating Capacity" column - the role the logbook's owner played on
 * a given flight, distinct from {@link FlightEntry#getPilotInCommandId()} (who was Captain) and from
 * {@link FlightEntry#getDualMinutes()} (a student's progress toward solo). See
 * docs/plans/holder-operating-capacity.md and docs/GLOSSARY.md.
 */
public enum HolderOperatingCapacity {
    PILOT_IN_COMMAND("P1"),
    PILOT_IN_COMMAND_UNDER_SUPERVISION("P1/S"),
    SECOND_PILOT("P2"),
    PILOT_UNDER_TRAINING("P.u/t"),
    NAVIGATOR("N.1"),
    NAVIGATOR_UNDER_SUPERVISION("N.2"),
    NAVIGATOR_UNDER_TRAINING("N.u/t"),
    RADIOTELEPHONY_OPERATOR("T.1"),
    RADIOTELEPHONY_OPERATOR_UNDER_TRAINING("T.u/t"),
    FLIGHT_ENGINEER("E.1");

    private final String notation;

    HolderOperatingCapacity(String notation) {
        this.notation = notation;
    }

    public String getNotation() {
        return notation;
    }
}
