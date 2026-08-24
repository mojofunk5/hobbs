package com.bonney.hobbs.endpoint;

import com.bonney.hobbs.domain.AdminRepository;
import com.bonney.hobbs.domain.Auth;
import com.bonney.hobbs.domain.PasswordReset;
import com.bonney.hobbs.domain.Session;
import com.bonney.hobbs.dto.LoginDto;
import com.bonney.hobbs.dto.PasswordResetConfirmDto;
import com.bonney.hobbs.dto.PasswordResetRequestDto;
import com.bonney.hobbs.dto.RegisterDto;
import com.bonney.hobbs.dto.SessionDto;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(AuthEndpoint.class);

    private final Auth auth;
    private final AdminRepository adminRepository;
    private final PasswordReset passwordReset;

    public AuthEndpoint(Auth auth, AdminRepository adminRepository, PasswordReset passwordReset) {
        this.auth = auth;
        this.adminRepository = adminRepository;
        this.passwordReset = passwordReset;
    }

    public void registerRoutes(JavalinConfig config) {
        config.routes.post("auth/register", this::register);
        config.routes.post("auth/login", this::login);
        config.routes.post("auth/password-reset", this::requestPasswordReset);
        config.routes.post("auth/password-reset/confirm", this::confirmPasswordReset);
    }

    @OpenApi(
        path = "/auth/register",
        methods = HttpMethod.POST,
        summary = "Register a new pilot",
        description = "Creates a pilot and a password-based auth identity in one operation. Requires a valid single-use referral code issued by an admin, or the one-time bootstrap code logged on first startup. Returns a session.",
        tags = {"Auth"},
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(from = RegisterDto.class)
        ),
        responses = {
            @OpenApiResponse(status = "201", content = @OpenApiContent(from = SessionDto.class)),
            @OpenApiResponse(status = "400", description = "Password does not meet complexity requirements, or email is not a valid address"),
            @OpenApiResponse(status = "403", description = "Invalid or already used referral code")
        }
    )
    private void register(Context context) {
        RegisterDto request = context.bodyAsClass(RegisterDto.class);
        logger.info("Registering pilot with email={}", request.getEmail());
        Session session = auth.register(request.getName(), request.getEmail(), request.getPassword(), request.getReferralCode());
        context.status(HttpStatus.CREATED).json(toSessionDto(session));
    }

    @OpenApi(
        path = "/auth/login",
        methods = HttpMethod.POST,
        summary = "Log in",
        description = "Verifies a password credential and returns a session.",
        tags = {"Auth"},
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(from = LoginDto.class)
        ),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = SessionDto.class)),
            @OpenApiResponse(status = "401", description = "Invalid credentials")
        }
    )
    private void login(Context context) {
        LoginDto request = context.bodyAsClass(LoginDto.class);
        logger.info("Login attempt for identifier={}", request.getIdentifier());
        Session session = auth.login(request.getIdentifier(), request.getPassword());
        context.json(toSessionDto(session));
    }

    @OpenApi(
        path = "/auth/password-reset",
        methods = HttpMethod.POST,
        summary = "Request a password reset code",
        description = "If the email belongs to a registered pilot, emails a 6-digit code (configurable TTL, "
                + "default 30 minutes) that can be used once to set a new password via POST /auth/password-reset/confirm. "
                + "Always returns 200 regardless of whether the email is registered, to avoid confirming account "
                + "existence. Requesting again for the same email invalidates any previously issued, still-unused code.",
        tags = {"Auth"},
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(from = PasswordResetRequestDto.class)
        ),
        responses = {
            @OpenApiResponse(status = "200")
        }
    )
    private void requestPasswordReset(Context context) {
        PasswordResetRequestDto request = context.bodyAsClass(PasswordResetRequestDto.class);
        logger.info("Password reset requested for email={}", request.getEmail());
        passwordReset.requestReset(request.getEmail());
        context.status(HttpStatus.OK);
    }

    @OpenApi(
        path = "/auth/password-reset/confirm",
        methods = HttpMethod.POST,
        summary = "Confirm a password reset",
        description = "Verifies the 6-digit code emailed by POST /auth/password-reset, sets a new password, and "
                + "returns a session (the pilot is logged in). The code is single-use and is consumed on success. "
                + "An invalid, expired, already-used, or mismatched-email code all return the same 400 to avoid "
                + "leaking which one it was.",
        tags = {"Auth"},
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(from = PasswordResetConfirmDto.class)
        ),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = SessionDto.class)),
            @OpenApiResponse(status = "400", description = "New password does not meet complexity requirements, or the code is invalid/expired/used")
        }
    )
    private void confirmPasswordReset(Context context) {
        PasswordResetConfirmDto request = context.bodyAsClass(PasswordResetConfirmDto.class);
        Session session = passwordReset.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        context.json(toSessionDto(session));
    }

    private SessionDto toSessionDto(Session session) {
        return new SessionDto(
                session.getSessionId().value(),
                session.getPilot().getId().value(),
                session.getPilot().getName(),
                adminRepository.isAdmin(session.getPilot().getId()));
    }
}
