package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

import java.util.List;

@OpenApiName("PilotPage")
public class PilotPageDto {

    private final List<PilotDto> pilots;
    private final int page;
    private final int pageSize;
    private final int total;

    @JsonCreator
    public PilotPageDto(
            @JsonProperty("pilots") List<PilotDto> pilots,
            @JsonProperty("page") int page,
            @JsonProperty("pageSize") int pageSize,
            @JsonProperty("total") int total) {
        this.pilots = pilots;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public List<PilotDto> getPilots() {
        return pilots;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotal() {
        return total;
    }
}
