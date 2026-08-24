package com.bonney.hobbs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record AppConfig(String dbUrl, String dbUsername, String dbPassword, int requestsPerSecond,
                         boolean docsEnabled, int sessionTtlHours, String corsAllowedOrigin, boolean trustProxy,
                         String smtpHost, int smtpPort, String smtpUsername, String smtpPassword,
                         String emailFromAddress, String frontendBaseUrl, int referralCodeTtlHours,
                         int passwordResetCodeTtlMinutes, int loginThrottleMaxAttempts, int loginThrottleWindowMinutes,
                         int passwordResetThrottleMaxAttempts, int passwordResetThrottleWindowMinutes) {

    private static final int DEFAULT_SESSION_TTL_HOURS = 24;
    private static final int DEFAULT_SMTP_PORT = 587;
    private static final int DEFAULT_REFERRAL_CODE_TTL_HOURS = 168;
    private static final int DEFAULT_PASSWORD_RESET_CODE_TTL_MINUTES = 30;
    private static final int DEFAULT_LOGIN_THROTTLE_MAX_ATTEMPTS = 10;
    private static final int DEFAULT_LOGIN_THROTTLE_WINDOW_MINUTES = 15;
    private static final int DEFAULT_PASSWORD_RESET_THROTTLE_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_PASSWORD_RESET_THROTTLE_WINDOW_MINUTES = 15;

    public static AppConfig fromClasspath() {
        try (InputStream stream = AppConfig.class.getResourceAsStream("/application.properties")) {
            var props = new Properties();
            props.load(stream);
            return new AppConfig(
                    envOrProperty("DB_URL", props, "db.url"),
                    envOrProperty("DB_USERNAME", props, "db.username"),
                    envOrProperty("DB_PASSWORD", props, "db.password"),
                    Integer.parseInt(props.getProperty("rate.requests-per-second")),
                    Boolean.parseBoolean(envOrProperty("DOCS_ENABLED", props, "docs.enabled")),
                    Integer.parseInt(props.getProperty("session.ttl-hours", String.valueOf(DEFAULT_SESSION_TTL_HOURS))),
                    envOrProperty("CORS_ALLOWED_ORIGIN", props, "cors.allowed-origin"),
                    Boolean.parseBoolean(envOrProperty("TRUST_PROXY", props, "trust-proxy")),
                    envOrProperty("SMTP_HOST", props, "smtp.host"),
                    Integer.parseInt(props.getProperty("smtp.port", String.valueOf(DEFAULT_SMTP_PORT))),
                    envOrProperty("SMTP_USERNAME", props, "smtp.username"),
                    envOrProperty("SMTP_PASSWORD", props, "smtp.password"),
                    envOrProperty("EMAIL_FROM_ADDRESS", props, "email.from-address"),
                    envOrProperty("FRONTEND_BASE_URL", props, "frontend.base-url"),
                    Integer.parseInt(props.getProperty("referral-code.ttl-hours", String.valueOf(DEFAULT_REFERRAL_CODE_TTL_HOURS))),
                    Integer.parseInt(props.getProperty("password-reset-code.ttl-minutes", String.valueOf(DEFAULT_PASSWORD_RESET_CODE_TTL_MINUTES))),
                    Integer.parseInt(props.getProperty("login-throttle.max-attempts", String.valueOf(DEFAULT_LOGIN_THROTTLE_MAX_ATTEMPTS))),
                    Integer.parseInt(props.getProperty("login-throttle.window-minutes", String.valueOf(DEFAULT_LOGIN_THROTTLE_WINDOW_MINUTES))),
                    Integer.parseInt(props.getProperty("password-reset-throttle.max-attempts", String.valueOf(DEFAULT_PASSWORD_RESET_THROTTLE_MAX_ATTEMPTS))),
                    Integer.parseInt(props.getProperty("password-reset-throttle.window-minutes", String.valueOf(DEFAULT_PASSWORD_RESET_THROTTLE_WINDOW_MINUTES)))
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    /**
     * Environment variables take precedence over application.properties so real credentials can be
     * supplied at deploy time (e.g. via docker-compose) without ever being committed to the repo.
     */
    private static String envOrProperty(String envVar, Properties props, String property) {
        String value = System.getenv(envVar);
        return value != null ? value : props.getProperty(property);
    }
}
