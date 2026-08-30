package com.bonney.hobbs.client;

import com.bonney.hobbs.dto.AircraftDto;
import com.bonney.hobbs.dto.ClaimInviteRequestDto;
import com.bonney.hobbs.dto.CreateFlightEntryDto;
import com.bonney.hobbs.dto.CreatePilotDto;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import com.bonney.hobbs.dto.FlightEntryDto;
import com.bonney.hobbs.dto.HealthDto;
import com.bonney.hobbs.dto.InvitePilotDto;
import com.bonney.hobbs.dto.LoginDto;
import com.bonney.hobbs.dto.PasswordResetConfirmDto;
import com.bonney.hobbs.dto.PasswordResetRequestDto;
import com.bonney.hobbs.dto.PendingInviteDto;
import com.bonney.hobbs.dto.PilotPageDto;
import com.bonney.hobbs.dto.PilotSummaryDto;
import com.bonney.hobbs.dto.ReferralCodeDto;
import com.bonney.hobbs.dto.RegisterDto;
import com.bonney.hobbs.dto.SessionDto;
import com.bonney.hobbs.dto.UpdatePilotAdminDto;
import com.bonney.hobbs.dto.VersionDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.RequestInterceptor;
import feign.Target;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.UUID;

public interface HobbsClient {

    static HobbsClient create(String url) {
        return create(url, new OkHttpClient.Builder().build());
    }

    static HobbsClient create(String url, OkHttpClient okHttpClient) {
        return builder(url, okHttpClient).build().newInstance(new Target.HardCodedTarget<>(HobbsClient.class, url));
    }

    static HobbsClient withAuth(String url, OkHttpClient okHttpClient, UUID sessionId) {
        RequestInterceptor authHeader = template -> template.header("Authorization", "Bearer " + sessionId);
        return builder(url, okHttpClient).requestInterceptor(authHeader).build().newInstance(new Target.HardCodedTarget<>(HobbsClient.class, url));
    }

    private static Feign.Builder builder(String url, OkHttpClient okHttpClient) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return Feign.builder()
                .client(new feign.okhttp.OkHttpClient(okHttpClient))
                .encoder(new JacksonEncoder(mapper))
                .decoder(new JacksonDecoder(mapper));
    }

    @RequestLine("GET /health")
    HealthDto health();

    @RequestLine("GET /version")
    VersionDto version();

    @RequestLine("POST /auth/register")
    SessionDto register(RegisterDto request);

    @RequestLine("POST /auth/login")
    SessionDto login(LoginDto request);

    @RequestLine("POST /auth/password-reset")
    void requestPasswordReset(PasswordResetRequestDto request);

    @RequestLine("POST /auth/password-reset/confirm")
    SessionDto confirmPasswordReset(PasswordResetConfirmDto request);

    @RequestLine("POST /admin/invite")
    ReferralCodeDto invitePilot(InvitePilotDto request);

    @RequestLine("DELETE /admin/invite?email={email}")
    void cancelInvite(@Param("email") String email);

    @RequestLine("GET /admin/invites")
    List<PendingInviteDto> adminListInvites();

    @RequestLine("GET /admin/pilots")
    PilotPageDto adminListPilots();

    @RequestLine("GET /admin/pilots?page={page}&pageSize={pageSize}&sort={sort}&order={order}")
    PilotPageDto adminListPilots(@Param("page") int page, @Param("pageSize") int pageSize,
                                  @Param("sort") String sort, @Param("order") String order);

    @RequestLine("PATCH /admin/pilot/{pilotId}")
    void adminUpdatePilot(@Param("pilotId") UUID pilotId, UpdatePilotAdminDto request);

    @RequestLine("DELETE /admin/pilot/{pilotId}")
    void adminDeletePilot(@Param("pilotId") UUID pilotId);

    @RequestLine("DELETE /admin/pilot/{pilotId}/sessions")
    void adminExpireSessions(@Param("pilotId") UUID pilotId);

    @RequestLine("POST /admin/pilot/{pilotId}/password-reset")
    void adminSendPasswordReset(@Param("pilotId") UUID pilotId);

    @RequestLine("GET /pilot")
    List<PilotSummaryDto> searchPilots();

    @RequestLine("GET /pilot?search={search}")
    List<PilotSummaryDto> searchPilots(@Param("search") String search);

    @RequestLine("POST /pilot")
    PilotSummaryDto createPilot(CreateUnclaimedPilotDto request);

    @RequestLine("PUT /pilot/{pilotId}")
    void updatePilot(@Param("pilotId") UUID pilotId, CreatePilotDto pilot);

    @RequestLine("DELETE /pilot/{pilotId}")
    void deletePilot(@Param("pilotId") UUID pilotId);

    @RequestLine("POST /pilot/{pilotId}/invite")
    ReferralCodeDto inviteToClaimPilot(@Param("pilotId") UUID pilotId, ClaimInviteRequestDto request);

    @RequestLine("GET /aircraft?search={search}")
    List<AircraftDto> searchAircraft(@Param("search") String search);

    @RequestLine("POST /flight")
    FlightEntryDto createFlightEntry(CreateFlightEntryDto request);

    @RequestLine("GET /flight")
    List<FlightEntryDto> listFlightEntries();

    @RequestLine("GET /flight/{flightEntryId}")
    FlightEntryDto getFlightEntry(@Param("flightEntryId") UUID flightEntryId);
}
