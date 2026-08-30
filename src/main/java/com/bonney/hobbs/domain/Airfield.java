package com.bonney.hobbs.domain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Reference data seeded from OurAirports' GB dataset (see docs/plans/airfield-picker.md), not
 * pilot-submitted - same self-owned reference-data pattern as {@link Aircraft}. Coordinates are
 * carried for the not-yet-built sunset/sunrise-table night-time derivation (see CLAUDE.md's Open
 * work), not consumed by anything yet.
 *
 * <p>{@code icaoCode} is nullable - some small GB strips in the source dataset genuinely don't have
 * one. {@code sourceName}/{@code sourceId} (e.g. {@code "ourairports"} + their own row id) are the
 * upsert key the re-runnable import job matches on - internal to this entity only, never exposed on
 * {@code AirfieldDto}.
 */
public class Airfield {

    private final AirfieldId id;
    private final String icaoCode;
    private final String name;
    private final String municipality;
    private final String isoCountry;
    private final String isoRegion;
    private final double latitude;
    private final double longitude;
    private final Integer elevationFt;
    private final String type;
    private final String sourceName;
    private final String sourceId;

    public Airfield(AirfieldId id, String icaoCode, String name, String municipality, String isoCountry,
                     String isoRegion, double latitude, double longitude, Integer elevationFt, String type,
                     String sourceName, String sourceId) {
        this.id = id;
        this.icaoCode = icaoCode;
        this.name = name;
        this.municipality = municipality;
        this.isoCountry = isoCountry;
        this.isoRegion = isoRegion;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevationFt = elevationFt;
        this.type = type;
        this.sourceName = sourceName;
        this.sourceId = sourceId;
    }

    public AirfieldId getId() {
        return id;
    }

    public String getIcaoCode() {
        return icaoCode;
    }

    public String getName() {
        return name;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getIsoCountry() {
        return isoCountry;
    }

    public String getIsoRegion() {
        return isoRegion;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Integer getElevationFt() {
        return elevationFt;
    }

    public String getType() {
        return type;
    }

    /**
     * Import-only - the upsert key's source system name (e.g. {@code "ourairports"}). Never added
     * to {@code AirfieldDto}.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Import-only - the upsert key's row id within the source system. Never added to
     * {@code AirfieldDto}.
     */
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Airfield that = (Airfield) o;
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
                .append("icaoCode", icaoCode)
                .append("name", name)
                .append("type", type)
                .toString();
    }
}
