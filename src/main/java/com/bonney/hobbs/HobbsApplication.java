package com.bonney.hobbs;

import com.bonney.hobbs.domain.AccountRepository;
import com.bonney.hobbs.domain.Accounts;
import com.bonney.hobbs.domain.AdminBootstrap;
import com.bonney.hobbs.domain.AdminRepository;
import com.bonney.hobbs.domain.AircraftImportJob;
import com.bonney.hobbs.domain.AircraftRepository;
import com.bonney.hobbs.domain.AirfieldImportJob;
import com.bonney.hobbs.domain.AirfieldRepository;
import com.bonney.hobbs.domain.Auth;
import com.bonney.hobbs.domain.AuthIdentityRepository;
import com.bonney.hobbs.domain.DuplicateEmailException;
import com.bonney.hobbs.domain.EmailSender;
import com.bonney.hobbs.domain.FailedAttemptRepository;
import com.bonney.hobbs.domain.FlightEntryRepository;
import com.bonney.hobbs.domain.InvalidAircraftSearchException;
import com.bonney.hobbs.domain.InvalidCredentialsException;
import com.bonney.hobbs.domain.InvalidEmailException;
import com.bonney.hobbs.domain.InvalidNameException;
import com.bonney.hobbs.domain.InvalidPageSizeException;
import com.bonney.hobbs.domain.InvalidPasswordException;
import com.bonney.hobbs.domain.InvalidPasswordResetCodeException;
import com.bonney.hobbs.domain.InvalidReferralCodeException;
import com.bonney.hobbs.domain.Logbook;
import com.bonney.hobbs.domain.PasswordHasher;
import com.bonney.hobbs.domain.PasswordReset;
import com.bonney.hobbs.domain.PasswordResetCodeRepository;
import com.bonney.hobbs.domain.PilotRepository;
import com.bonney.hobbs.domain.Pilots;
import com.bonney.hobbs.domain.RateLimitRepository;
import com.bonney.hobbs.domain.ReferralCodeRepository;
import com.bonney.hobbs.domain.ScheduledCleanupJobs;
import com.bonney.hobbs.domain.SessionRepository;
import com.bonney.hobbs.domain.Sessions;
import com.bonney.hobbs.domain.SmtpEmailSender;
import com.bonney.hobbs.endpoint.AdminEndpoint;
import com.bonney.hobbs.endpoint.AircraftEndpoint;
import com.bonney.hobbs.endpoint.AuthEndpoint;
import com.bonney.hobbs.endpoint.FlightEntryEndpoint;
import com.bonney.hobbs.endpoint.HealthEndpoint;
import com.bonney.hobbs.endpoint.PilotEndpoint;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.UUID;

public class HobbsApplication {

    private final Javalin javalin;
    private final AdminBootstrap adminBootstrap;
    private final DataSource dataSource;
    private final ScheduledCleanupJobs scheduledCleanupJobs;

    public HobbsApplication(int port) {
        this(port, AppConfig.fromClasspath());
    }

    public HobbsApplication(int port, AppConfig config) {
        this(port, config, new SmtpEmailSender(config.smtpHost(), config.smtpPort(), config.smtpUsername(),
                config.smtpPassword(), config.emailFromAddress()));
    }

    /**
     * Overload taking an explicit {@link EmailSender} so tests can inject a fake instead of the real
     * SMTP one the other constructor always builds from {@code config} - avoids every test that
     * registers a pilot (i.e. most of the integration suite) needing a working mail server.
     */
    public HobbsApplication(int port, AppConfig config, EmailSender emailSender) {
        this(port, config, emailSender, Clock.systemUTC());
    }

    /**
     * Overload taking an explicit {@link Clock}, threaded into {@link FailedAttemptRepository} - lets
     * a test fix "now" so a login/password-reset throttle assertion doesn't race the real wall clock
     * (same reasoning as {@link RateLimitRepository}'s own injectable {@code Clock}). Every other
     * caller, including the other constructors here, gets the real {@code Clock.systemUTC()}.
     */
    public HobbsApplication(int port, AppConfig config, EmailSender emailSender, Clock clock) {
        this.dataSource = createDataSource(config);

        // jOOQ's code generator introspects the schema via an in-memory H2 instance (see the
        // DDLDatabase config in build.gradle), which uppercases unquoted identifiers, baking
        // quoted-uppercase names into the generated code. Postgres lowercases unquoted identifiers
        // instead, so quoted rendering would look for the wrong case there. Disabling quoting lets
        // each database apply its own folding convention so both resolve to the same table/column.
        Settings settings = new Settings().withRenderQuotedNames(RenderQuotedNames.NEVER);
        DSLContext dsl = DSL.using(dataSource, JDBCUtils.dialect(config.dbUrl()), settings);

        Pilots pilots = new Pilots(new PilotRepository(dsl));
        AuthIdentityRepository authIdentityRepository = new AuthIdentityRepository(dsl);
        Accounts accounts = new Accounts(new AccountRepository(dsl), authIdentityRepository);
        SessionRepository sessionRepository = new SessionRepository(dsl);
        Sessions sessions = new Sessions(sessionRepository, config.sessionTtlHours());
        AdminRepository adminRepository = new AdminRepository(dsl);
        ReferralCodeRepository referralCodeRepository = new ReferralCodeRepository(dsl);
        PasswordResetCodeRepository passwordResetCodeRepository = new PasswordResetCodeRepository(dsl);
        PasswordHasher passwordHasher = new PasswordHasher();
        this.adminBootstrap = new AdminBootstrap(adminRepository);
        FailedAttemptRepository failedAttemptRepository = new FailedAttemptRepository(dsl, clock);
        Auth auth = new Auth(pilots, accounts, authIdentityRepository, sessions, passwordHasher,
                adminBootstrap, adminRepository, referralCodeRepository, failedAttemptRepository,
                config.loginThrottleMaxAttempts(), Duration.ofMinutes(config.loginThrottleWindowMinutes()));
        PasswordReset passwordReset = new PasswordReset(pilots, accounts, authIdentityRepository, passwordHasher,
                passwordResetCodeRepository, sessions, emailSender, config.frontendBaseUrl(), config.passwordResetCodeTtlMinutes(),
                failedAttemptRepository, config.passwordResetThrottleMaxAttempts(),
                Duration.ofMinutes(config.passwordResetThrottleWindowMinutes()));

        Logbook logbook = new Logbook(new FlightEntryRepository(dsl), new AircraftRepository(dsl));

        HealthEndpoint healthEndpoint = new HealthEndpoint();
        FlightEntryEndpoint flightEntryEndpoint = new FlightEntryEndpoint(logbook);
        AircraftEndpoint aircraftEndpoint = new AircraftEndpoint(logbook);
        PilotEndpoint pilotEndpoint = new PilotEndpoint(pilots, accounts, adminRepository, referralCodeRepository,
                emailSender, config.frontendBaseUrl(), config.referralCodeTtlHours());
        AuthEndpoint authEndpoint = new AuthEndpoint(auth, adminRepository, passwordReset);
        AdminEndpoint adminEndpoint = new AdminEndpoint(pilots, accounts, adminRepository, referralCodeRepository,
                emailSender, config.frontendBaseUrl(), config.referralCodeTtlHours(), sessions, passwordReset);

        RateLimitRepository rateLimitRepository = new RateLimitRepository(dsl);
        RateLimiter rateLimiter = new RateLimiter(rateLimitRepository, config.requestsPerSecond(), config.trustProxy());
        SessionAuthFilter sessionAuthFilter = new SessionAuthFilter(sessions, adminRepository);

        this.scheduledCleanupJobs = new ScheduledCleanupJobs();
        scheduledCleanupJobs.schedule("expired-sessions", () -> sessionRepository.deleteExpired(config.sessionTtlHours()));
        scheduledCleanupJobs.schedule("stale-rate-limit-buckets", rateLimitRepository::deleteStale);
        scheduledCleanupJobs.schedule("stale-failed-attempts", failedAttemptRepository::deleteStale);

        this.javalin = Javalin.create(javalinConfig -> {
            javalinConfig.jsonMapper(new io.javalin.json.JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));
            javalinConfig.bundledPlugins.enableCors(cors -> cors.addRule(it -> {
                if (config.corsAllowedOrigin() != null && !config.corsAllowedOrigin().isBlank()) {
                    it.allowHost(config.corsAllowedOrigin());
                } else {
                    it.anyHost();
                }
            }));
            javalinConfig.validation.register(UUID.class, UUID::fromString);
            if (config.docsEnabled()) {
                javalinConfig.registerPlugin(new OpenApiPlugin(cfg -> cfg
                        .withDefinitionConfiguration((version, definition) -> definition
                                .info(info -> info
                                        .title("Hobbs API")
                                        .version("1.0")
                                        .description("REST API for the Hobbs PPL logbook"))
                                .withBearerAuth("BearerAuth")
                                .withGlobalSecurity("BearerAuth", security -> {}))));
                javalinConfig.registerPlugin(new SwaggerPlugin(cfg -> {}));
            }
            javalinConfig.routes.before(rateLimiter::handle);
            javalinConfig.routes.before(sessionAuthFilter::handle);
            javalinConfig.routes.exception(NoSuchElementException.class, (e, ctx) -> ctx.status(HttpStatus.NOT_FOUND));
            javalinConfig.routes.exception(DuplicateEmailException.class, (e, ctx) -> ctx.status(HttpStatus.CONFLICT));
            javalinConfig.routes.exception(InvalidCredentialsException.class, (e, ctx) -> ctx.status(HttpStatus.UNAUTHORIZED));
            javalinConfig.routes.exception(InvalidPasswordException.class, (e, ctx) -> ctx.status(HttpStatus.BAD_REQUEST));
            javalinConfig.routes.exception(InvalidPageSizeException.class, (e, ctx) -> ctx.status(HttpStatus.BAD_REQUEST));
            javalinConfig.routes.exception(InvalidAircraftSearchException.class, (e, ctx) -> ctx.status(HttpStatus.BAD_REQUEST));
            javalinConfig.routes.exception(InvalidEmailException.class, (e, ctx) -> ctx.status(HttpStatus.BAD_REQUEST));
            javalinConfig.routes.exception(InvalidNameException.class, (e, ctx) -> ctx.status(HttpStatus.BAD_REQUEST));
            javalinConfig.routes.exception(InvalidPasswordResetCodeException.class, (e, ctx) -> ctx.status(HttpStatus.BAD_REQUEST));
            javalinConfig.routes.exception(InvalidReferralCodeException.class, (e, ctx) -> ctx.status(HttpStatus.FORBIDDEN));
            healthEndpoint.registerRoutes(javalinConfig);
            flightEntryEndpoint.registerRoutes(javalinConfig);
            aircraftEndpoint.registerRoutes(javalinConfig);
            pilotEndpoint.registerRoutes(javalinConfig);
            authEndpoint.registerRoutes(javalinConfig);
            adminEndpoint.registerRoutes(javalinConfig);
        })
        .start(port);
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && "migrate".equals(args[0])) {
            migrate(AppConfig.fromClasspath());
            return;
        }
        if (args.length == 2 && "import-aircraft".equals(args[0])) {
            importAircraft(AppConfig.fromClasspath(), Path.of(args[1]));
            return;
        }
        if (args.length == 2 && "import-airfields".equals(args[0])) {
            importAirfields(AppConfig.fromClasspath(), Path.of(args[1]));
            return;
        }
        new HobbsApplication(Integer.parseInt(args[0]));
    }

    /**
     * Runs pending Flyway migrations and exits, without starting the server. Schema changes must
     * always be their own deliberate deploy step, never an implicit side effect of the app booting -
     * a routine restart with zero schema changes shouldn't re-run
     * Flyway, and a new migration shouldn't land silently at whatever moment the app happens to
     * restart, with no chance to review, sequence, or roll it back separately from the code deploy.
     */
    public static void migrate(AppConfig config) {
        DataSource dataSource = createDataSource(config);
        try {
            Flyway.configure().dataSource(dataSource).load().migrate();
        } finally {
            closeDataSource(dataSource);
        }
    }

    /**
     * Runs the OpenSky aircraft reference-data import/reconciliation job against an
     * already-downloaded CSV and exits, without starting the server - same "explicit, deliberate
     * step" reasoning as {@link #migrate}. See {@link AircraftImportJob} for the upsert-by-registration
     * behaviour; re-running this against the same or a newer CSV is always safe.
     */
    public static void importAircraft(AppConfig config, Path csvPath) throws IOException {
        DataSource dataSource = createDataSource(config);
        try {
            Settings settings = new Settings().withRenderQuotedNames(RenderQuotedNames.NEVER);
            DSLContext dsl = DSL.using(dataSource, JDBCUtils.dialect(config.dbUrl()), settings);
            AircraftImportJob job = new AircraftImportJob(new AircraftRepository(dsl));
            try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
                job.importFrom(reader);
            }
        } finally {
            closeDataSource(dataSource);
        }
    }

    /**
     * Runs the OurAirports airfield reference-data import/reconciliation job against an
     * already-downloaded CSV and exits, without starting the server - same "explicit, deliberate
     * step" reasoning as {@link #migrate}. See {@link AirfieldImportJob} for the
     * upsert-by-(sourceName, sourceId) behaviour and its GB-only/active/fixed-wing filtering;
     * re-running this against the same or a newer CSV is always safe.
     */
    public static void importAirfields(AppConfig config, Path csvPath) throws IOException {
        DataSource dataSource = createDataSource(config);
        try {
            Settings settings = new Settings().withRenderQuotedNames(RenderQuotedNames.NEVER);
            DSLContext dsl = DSL.using(dataSource, JDBCUtils.dialect(config.dbUrl()), settings);
            AirfieldImportJob job = new AirfieldImportJob(new AirfieldRepository(dsl));
            try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
                job.importFrom(reader);
            }
        } finally {
            closeDataSource(dataSource);
        }
    }

    private static DataSource createDataSource(AppConfig config) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.dbUrl());
        hikariConfig.setUsername(config.dbUsername());
        hikariConfig.setPassword(config.dbPassword());
        hikariConfig.setConnectionTimeout(5000);
        return new HikariDataSource(hikariConfig);
    }

    private static void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to close data source", e);
            }
        }
    }

    public int getPort() {
        return javalin.port();
    }

    public AdminBootstrap getAdminBootstrap() {
        return adminBootstrap;
    }

    public void stop() {
        scheduledCleanupJobs.stop();
        javalin.stop();
        closeDataSource(dataSource);
    }
}
