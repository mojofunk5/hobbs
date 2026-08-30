package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.util.UUID;

/**
 * Backs GET /airfield?search= - deliberately excludes sourceName/sourceId (the import job's upsert
 * key, internal to the domain entity only - see Airfield's Javadoc and
 * docs/plans/airfield-picker.md's Confirmed decisions).
 */
@OpenApiName("Airfield")
public class AirfieldDto {

    private final UUID id;
    private final String icaoCode;
    private final String name;
    private final String municipality;
    private final String isoCountry;
    private final String isoRegion;
    private final double latitude;
    private final double longitude;
    private final Integer elevationFt;
    private final String type;

    public AirfieldDto(@JsonProperty("id") UUID id, @JsonProperty("icaoCode") String icaoCode,
                        @JsonProperty("name") String name, @JsonProperty("municipality") String municipality,
                        @JsonProperty("isoCountry") String isoCountry, @JsonProperty("isoRegion") String isoRegion,
                        @JsonProperty("latitude") double latitude, @JsonProperty("longitude") double longitude,
                        @JsonProperty("elevationFt") Integer elevationFt, @JsonProperty("type") String type) {
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
    }

    public UUID getId() {
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
}
