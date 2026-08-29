package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.SessionAuthFilter;
import com.bonney.hobbs.domain.Account;
import com.bonney.hobbs.domain.Accounts;
import com.bonney.hobbs.domain.AdminRepository;
import com.bonney.hobbs.domain.DuplicateEmailException;
import com.bonney.hobbs.domain.EmailSender;
import com.bonney.hobbs.domain.Pilot;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.domain.Pilots;
import com.bonney.hobbs.domain.ReferralCode;
import com.bonney.hobbs.domain.ReferralCodeRepository;
import com.bonney.hobbs.dto.ClaimInviteRequestDto;
import com.bonney.hobbs.dto.CreatePilotDto;
import com.bonney.hobbs.dto.CreateUnclaimedPilotDto;
import com.bonney.hobbs.dto.PilotSummaryDto;
import com.bonney.hobbs.dto.ReferralCodeDto;
import com.bonney.hobbs.email.EmailTemplate;
import com.bonney.hobbs.email.InviteEmailTemplate;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

public class PilotEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(PilotEndpoint.class);

    private final Pilots pilots;
    private final Accounts accounts;
    private final AdminRepository adminRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;
    private final int referralCodeTtlHours;

    public PilotEndpoint(Pilots pilots, Accounts accounts, AdminRepository adminRepository,
                          ReferralCodeRepository referralCodeRepository, EmailSender emailSender,
                          String frontendBaseUrl, int referralCodeTtlHours) {
        this.pilots = pilots;
        this.accounts = accounts;
        this.adminRepository = adminRepository;
        this.referralCodeRepository = referralCodeRepository;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.referralCodeTtlHours = referralCodeTtlHours;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.post("pilot", this::createPilot);
        config.routes.put("pilot/{pilotId}", this::updatePilot);
        config.routes.delete("pilot/{pilotId}", this::deletePilot);
        config.routes.post("pilot/{pilotId}/invite", this::inviteToClaim);
    }

    @OpenApi(
        path = "/pilot",
        methods = HttpMethod.POST,
        summary = "Create an unclaimed pilot",
        description = "Creates a Pilot record for someone recordable on a flight who hasn't signed up yet (e.g. a "
                + "co-pilot) - `createdBy` is set to the authenticated caller, who can later invite this pilot to "
                + "claim the record via POST /pilot/{pilotId}/invite. Authenticated, not admin-gated.",
        tags = {"Pilot"},
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateUnclaimedPilotDto.class)),
        responses = {
            @OpenApiResponse(status = "201", content = @OpenApiContent(from = PilotSummaryDto.class)),
            @OpenApiResponse(status = "400", description = "Name is missing or too long")
        }
    )
    private void createPilot(Context context) {
        PilotId callerId = context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
        CreateUnclaimedPilotDto request = context.bodyAsClass(CreateUnclaimedPilotDto.class);
        Pilot pilot = pilots.create(request.getName(), callerId);
        logger.info("PilotId={} created unclaimed pilotId={}", callerId, pilot.getId());
        context.status(HttpStatus.CREATED).json(new PilotSummaryDto(pilot.getId().value(), pilot.getName()));
    }

    @OpenApi(
        path = "/pilot/{pilotId}",
        methods = HttpMethod.PUT,
        summary = "Update a pilot",
        description = "Updates a pilot's name and email.",
        tags = {"Pilot"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(
                from = CreatePilotDto.class,
                example = "{\"name\": \"Alice Smith\", \"email\": \"alice@example.com\"}"
            )
        ),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "400", description = "Email is not a valid address"),
            @OpenApiResponse(status = "403", description = "Not the authenticated pilot"),
            @OpenApiResponse(status = "404", description = "Pilot not found")
        }
    )
    private void updatePilot(Context context) {
        PilotId pilotId = getPilotId(context);
        if (!isAuthenticatedPilot(context, pilotId)) {
            context.status(HttpStatus.FORBIDDEN);
            return;
        }
        CreatePilotDto request = context.bodyAsClass(CreatePilotDto.class);
        logger.info("Updating pilot with pilotId={} to name={}", pilotId, request.getName());
        if (pilots.get(pilotId).isEmpty()) {
            context.status(HttpStatus.NOT_FOUND);
            return;
        }
        pilots.updateName(pilotId, request.getName());
        Account account = accounts.get(pilotId).orElseThrow(NoSuchElementException::new);
        if (!account.getEmail().equals(request.getEmail())) {
            accounts.updateEmail(pilotId, request.getEmail());
        }
    }

    @OpenApi(
        path = "/pilot/{pilotId}",
        methods = HttpMethod.DELETE,
        summary = "Delete your own account",
        description = "Deletes your account (login credentials) - the Pilot record and your logged flight history "
                + "are preserved under the same PilotId, which reverts to unclaimed.",
        tags = {"Pilot"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "403", description = "Not the authenticated pilot")
        }
    )
    private void deletePilot(Context context) {
        PilotId pilotId = getPilotId(context);
        if (!isAuthenticatedPilot(context, pilotId)) {
            context.status(HttpStatus.FORBIDDEN);
            return;
        }
        logger.info("Deleting the account for pilotId={}", pilotId);
        accounts.delete(pilotId);
    }

    @OpenApi(
        path = "/pilot/{pilotId}/invite",
        methods = HttpMethod.POST,
        summary = "Invite an unclaimed pilot to claim their record",
        description = "Generates a single-use referral code scoped to the given email that, when registered with, "
                + "attaches to this existing Pilot record instead of creating a new one - William's mechanism to "
                + "invite Louis, a co-pilot he already logged, to take ownership of the record William created for "
                + "him. Authenticated; 403 unless the caller created this pilot record or is an admin.",
        tags = {"Pilot"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClaimInviteRequestDto.class)),
        responses = {
            @OpenApiResponse(status = "201", content = @OpenApiContent(from = ReferralCodeDto.class)),
            @OpenApiResponse(status = "403", description = "Caller did not create this pilot record and is not an admin"),
            @OpenApiResponse(status = "404", description = "No such pilot"),
            @OpenApiResponse(status = "409", description = "A pilot with that email already exists")
        }
    )
    private void inviteToClaim(Context context) {
        PilotId pilotId = getPilotId(context);
        PilotId callerId = context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
        Pilot target = pilots.get(pilotId).orElseThrow(NoSuchElementException::new);
        if (!callerId.equals(target.getCreatedBy()) && !adminRepository.isAdmin(callerId)) {
            context.status(HttpStatus.FORBIDDEN);
            return;
        }

        ClaimInviteRequestDto request = context.bodyAsClass(ClaimInviteRequestDto.class);
        if (accounts.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateEmailException(request.getEmail());
        }
        referralCodeRepository.expireUnusedForEmail(request.getEmail());
        String code = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        referralCodeRepository.save(new ReferralCode(code, callerId, now, request.getEmail(), now.plusHours(referralCodeTtlHours), pilotId));
        logger.info("PilotId={} invited email={} to claim pilotId={}", callerId, request.getEmail(), pilotId);

        try {
            String encodedEmail = URLEncoder.encode(request.getEmail(), StandardCharsets.UTF_8);
            // Pre-fills the Name field on the sign-up page with the name the record was created
            // under - still editable there, since it's whoever invited them guessing at their name.
            String link = frontendBaseUrl + "/create-pilot?code=" + code + "&email=" + encodedEmail
                    + "&name=" + URLEncoder.encode(target.getName(), StandardCharsets.UTF_8);
            EmailTemplate template = new InviteEmailTemplate(target.getName(), link, code, referralCodeTtlHours);
            emailSender.send(request.getEmail(), template.subject(), template.htmlBody());
        } catch (RuntimeException e) {
            logger.warn("Failed to send claim-invite email to={}, code is still valid and can be shared manually", request.getEmail(), e);
        }

        context.status(HttpStatus.CREATED).json(new ReferralCodeDto(code));
    }

    private boolean isAuthenticatedPilot(Context context, PilotId pilotId) {
        PilotId authenticatedPilotId = context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
        return pilotId.equals(authenticatedPilotId);
    }

    private PilotId getPilotId(Context context) {
        return PilotId.from(context.pathParamAsClass("pilotId", UUID.class).get());
    }
}
