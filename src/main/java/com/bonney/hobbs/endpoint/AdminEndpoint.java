package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.SessionAuthFilter;
import com.bonney.hobbs.domain.Account;
import com.bonney.hobbs.domain.Accounts;
import com.bonney.hobbs.domain.AdminRepository;
import com.bonney.hobbs.domain.DuplicateEmailException;
import com.bonney.hobbs.domain.EmailSender;
import com.bonney.hobbs.domain.InvalidPageSizeException;
import com.bonney.hobbs.domain.PasswordReset;
import com.bonney.hobbs.domain.PilotId;
import com.bonney.hobbs.domain.PilotListRow;
import com.bonney.hobbs.domain.Pilots;
import com.bonney.hobbs.domain.ReferralCode;
import com.bonney.hobbs.domain.ReferralCodeRepository;
import com.bonney.hobbs.domain.Sessions;
import com.bonney.hobbs.dto.InvitePilotDto;
import com.bonney.hobbs.dto.PendingInviteDto;
import com.bonney.hobbs.dto.PilotDto;
import com.bonney.hobbs.dto.PilotPageDto;
import com.bonney.hobbs.dto.ReferralCodeDto;
import com.bonney.hobbs.dto.UpdatePilotAdminDto;
import com.bonney.hobbs.email.EmailTemplate;
import com.bonney.hobbs.email.InviteEmailTemplate;
import com.bonney.hobbs.mapper.PilotMapper;
import com.bonney.hobbs.mapper.ReferralCodeMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

public class AdminEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(AdminEndpoint.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_COLUMNS = Set.of("name", "email", "disabled", "signedUpAt", "lastLoginAt");
    private static final Set<String> SORT_ORDERS = Set.of("asc", "desc");

    private final Pilots pilots;
    private final Accounts accounts;
    private final AdminRepository adminRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;
    private final int referralCodeTtlHours;
    private final Sessions sessions;
    private final PasswordReset passwordReset;

    public AdminEndpoint(Pilots pilots, Accounts accounts, AdminRepository adminRepository, ReferralCodeRepository referralCodeRepository,
                          EmailSender emailSender, String frontendBaseUrl, int referralCodeTtlHours, Sessions sessions,
                          PasswordReset passwordReset) {
        this.pilots = pilots;
        this.accounts = accounts;
        this.adminRepository = adminRepository;
        this.referralCodeRepository = referralCodeRepository;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.referralCodeTtlHours = referralCodeTtlHours;
        this.sessions = sessions;
        this.passwordReset = passwordReset;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.post("admin/invite", this::invitePilot);
        config.routes.delete("admin/invite", this::cancelInvite);
        config.routes.get("admin/invites", this::listInvites);
        config.routes.get("admin/pilots", this::listPilots);
        config.routes.patch("admin/pilot/{pilotId}", this::updatePilot);
        config.routes.delete("admin/pilot/{pilotId}", this::deletePilot);
        config.routes.delete("admin/pilot/{pilotId}/sessions", this::expireSessions);
        config.routes.post("admin/pilot/{pilotId}/password-reset", this::sendPasswordReset);
    }

    @OpenApi(
        path = "/admin/pilots",
        methods = HttpMethod.GET,
        summary = "List pilots",
        description = "Paginated, sortable list of all non-deleted pilots, including disabled ones, each with "
                + "when they signed up and when they last logged in (register, login, or a completed password "
                + "reset all count). Admin only.",
        tags = {"Admin"},
        queryParams = {
            @OpenApiParam(name = "page", type = Integer.class, description = "Page number, zero-based (default 0)"),
            @OpenApiParam(name = "pageSize", type = Integer.class, description = "Page size (default 10, max 100)"),
            @OpenApiParam(name = "sort", type = String.class, description = "Column to sort by: name, email, disabled, signedUpAt, lastLoginAt (default name; unrecognized values fall back to the default)"),
            @OpenApiParam(name = "order", type = String.class, description = "asc or desc (default asc; unrecognized values fall back to the default)")
        },
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = PilotPageDto.class)),
            @OpenApiResponse(status = "400", description = "pageSize exceeds the maximum of 100")
        }
    )
    private void listPilots(Context context) {
        int page = context.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int pageSize = context.queryParamAsClass("pageSize", Integer.class).getOrDefault(10);
        if (pageSize > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException(pageSize, MAX_PAGE_SIZE);
        }
        String sort = context.queryParam("sort");
        if (sort == null || !SORTABLE_COLUMNS.contains(sort)) {
            sort = "name";
        }
        String order = context.queryParam("order");
        if (order == null || !SORT_ORDERS.contains(order.toLowerCase())) {
            order = "asc";
        }

        List<PilotListRow> rows = pilots.listActivePage(sort, order, page * pageSize, pageSize);
        List<PilotDto> pilotDtos = rows.stream()
                .map(row -> PilotMapper.toPilotDto(row.pilot(), row.email(), row.disabled(), row.signedUpAt(), row.lastLoginAt()))
                .toList();
        int total = pilots.countActive();
        context.json(new PilotPageDto(pilotDtos, page, pageSize, total));
    }

    @OpenApi(
        path = "/admin/pilot/{pilotId}/password-reset",
        methods = HttpMethod.POST,
        summary = "Send a pilot a password reset link",
        description = "Triggers the same password-reset flow as POST /auth/password-reset (a fresh 6-digit code, "
                + "emailed with a reset link, invalidating any previous unused code) - admin-initiated and scoped "
                + "by pilotId rather than self-service by email. Admin only.",
        tags = {"Admin"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "404", description = "No such pilot")
        }
    )
    private void sendPasswordReset(Context context) {
        PilotId targetId = PilotId.from(context.pathParamAsClass("pilotId", UUID.class).get());
        Account account = accounts.get(targetId).orElseThrow(NoSuchElementException::new);
        passwordReset.requestReset(account.getEmail());
        logger.info("Admin sent a password reset link to pilotId={}", targetId);
    }

    @OpenApi(
        path = "/admin/invite",
        methods = HttpMethod.POST,
        summary = "Invite a new pilot by email",
        description = "Generates a single-use referral code scoped to the given email and sends it there as a "
                + "sign-up link. `name` is optional and only used to personalise the email greeting - the code "
                + "itself is still scoped by email, not name. The code expires after a fixed server-side TTL; "
                + "re-inviting the same email immediately retires any previous unused code for it and issues a "
                + "fresh one - that's the only renewal mechanism, there's no separate extend endpoint. Admin only. "
                + "The code is still returned even if the email fails to send, so it can be shared manually - a "
                + "mail-server hiccup shouldn't lose it.",
        tags = {"Admin"},
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InvitePilotDto.class)),
        responses = {
            @OpenApiResponse(status = "201", content = @OpenApiContent(from = ReferralCodeDto.class)),
            @OpenApiResponse(status = "409", description = "A pilot with that email already exists")
        }
    )
    private void invitePilot(Context context) {
        InvitePilotDto request = context.bodyAsClass(InvitePilotDto.class);
        if (accounts.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateEmailException(request.getEmail());
        }
        PilotId adminId = context.attribute(SessionAuthFilter.AUTHENTICATED_PILOT_ID);
        referralCodeRepository.expireUnusedForEmail(request.getEmail());
        String code = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        referralCodeRepository.save(new ReferralCode(code, adminId, now, request.getEmail(), now.plusHours(referralCodeTtlHours)));
        logger.info("Admin pilotId={} invited email={}", adminId, request.getEmail());

        try {
            String encodedEmail = URLEncoder.encode(request.getEmail(), StandardCharsets.UTF_8);
            String link = frontendBaseUrl + "/create-pilot?code=" + code + "&email=" + encodedEmail;
            // Pre-fills the Name field on the sign-up page - still editable there, not locked the way
            // email/code are, since an admin's guess at someone's name shouldn't be treated as final.
            if (request.getName() != null && !request.getName().isBlank()) {
                link += "&name=" + URLEncoder.encode(request.getName(), StandardCharsets.UTF_8);
            }
            EmailTemplate template = new InviteEmailTemplate(request.getName(), link, code, referralCodeTtlHours);
            emailSender.send(request.getEmail(), template.subject(), template.htmlBody());
        } catch (RuntimeException e) {
            logger.warn("Failed to send invite email to={}, code is still valid and can be shared manually", request.getEmail(), e);
        }

        context.status(HttpStatus.CREATED).json(new ReferralCodeDto(code));
    }

    @OpenApi(
        path = "/admin/invites",
        methods = HttpMethod.GET,
        summary = "List pending invites",
        description = "Lists invites that haven't been used to register yet, most recent first, including "
                + "already-expired ones so an admin can see what needs re-inviting. Doesn't include the referral "
                + "code itself - re-invite the email to issue and send a fresh one. Admin only.",
        tags = {"Admin"},
        responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PendingInviteDto[].class))
    )
    private void listInvites(Context context) {
        List<PendingInviteDto> invites = referralCodeRepository.listUnused().stream()
                .map(ReferralCodeMapper::toPendingInviteDto).toList();
        context.json(invites);
    }

    @OpenApi(
        path = "/admin/invite",
        methods = HttpMethod.DELETE,
        summary = "Cancel a pending invite",
        description = "Soft-cancels the unused referral code(s) for the given email so it can no longer be used "
                + "to register and stops showing up in GET /admin/invites - the row isn't removed from the "
                + "database, same as every other soft-delete in this domain (disabled_at, deleted_at, used_at). "
                + "Re-inviting the same email afterwards works as normal and issues a fresh code. Admin only.",
        tags = {"Admin"},
        queryParams = @OpenApiParam(name = "email", required = true),
        responses = @OpenApiResponse(status = "200")
    )
    private void cancelInvite(Context context) {
        String email = context.queryParamAsClass("email", String.class).get();
        referralCodeRepository.cancelUnusedForEmail(email);
        logger.info("Admin cancelled invite email={}", email);
    }

    @OpenApi(
        path = "/admin/pilot/{pilotId}",
        methods = HttpMethod.DELETE,
        summary = "Delete a pilot account",
        description = "Soft-deletes a pilot account. The pilot's logbook history is preserved. Admin only.",
        tags = {"Admin"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        responses = @OpenApiResponse(status = "200")
    )
    private void deletePilot(Context context) {
        PilotId targetId = PilotId.from(context.pathParamAsClass("pilotId", UUID.class).get());
        pilots.delete(targetId);
        logger.info("Admin deleted pilotId={}", targetId);
    }

    @OpenApi(
        path = "/admin/pilot/{pilotId}",
        methods = HttpMethod.PATCH,
        summary = "Update a pilot's account state",
        description = "Partial update of admin-controlled pilot account state. Currently supports `enabled` "
                + "(disables/re-enables login without deleting the account or logbook history) - fields omitted "
                + "from the request body are left untouched. Admin only.",
        tags = {"Admin"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdatePilotAdminDto.class)),
        responses = @OpenApiResponse(status = "200")
    )
    private void updatePilot(Context context) {
        PilotId targetId = PilotId.from(context.pathParamAsClass("pilotId", UUID.class).get());
        UpdatePilotAdminDto request = context.bodyAsClass(UpdatePilotAdminDto.class);
        if (request.getEnabled() != null) {
            accounts.get(targetId).orElseThrow(NoSuchElementException::new);
            if (request.getEnabled()) {
                accounts.enable(targetId);
                logger.info("Admin enabled pilotId={}", targetId);
            } else {
                accounts.disable(targetId);
                logger.info("Admin disabled pilotId={}", targetId);
            }
        }
    }

    @OpenApi(
        path = "/admin/pilot/{pilotId}/sessions",
        methods = HttpMethod.DELETE,
        summary = "Expire a pilot's sessions",
        description = "Force-logs-out a pilot by deleting all of their active sessions - their next "
                + "authenticated request will 401 and they'll need to log in again. Admin only.",
        tags = {"Admin"},
        pathParams = @OpenApiParam(name = "pilotId", type = UUID.class, required = true),
        responses = @OpenApiResponse(status = "200")
    )
    private void expireSessions(Context context) {
        PilotId targetId = PilotId.from(context.pathParamAsClass("pilotId", UUID.class).get());
        sessions.deleteAllForPilot(targetId);
        logger.info("Admin expired sessions for pilotId={}", targetId);
    }
}
