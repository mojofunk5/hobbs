package com.bonney.hobbs.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

@OpenApiName("InvitePilot")
public class InvitePilotDto {

    private final String email;
    private final String name;

    @JsonCreator
    public InvitePilotDto(@JsonProperty("email") String email, @JsonProperty("name") String name) {
        this.email = email;
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Optional - used only to personalise the invite email's greeting ("Hi Alice," vs "Hi,"). Not
     * persisted anywhere; the referral code itself is still scoped to the email, not a name.
     */
    public String getName() {
        return name;
    }
}
