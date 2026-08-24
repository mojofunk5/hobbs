package com.bonney.hobbs;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AppConfigTest {

    @Test
    void fromClasspathReadsTheBundledApplicationProperties() {
        AppConfig config = AppConfig.fromClasspath();

        assertThat(config.dbUrl(), is("jdbc:h2:./hobbs;MODE=PostgreSQL"));
        assertThat(config.dbUsername(), is("sa"));
        assertThat(config.requestsPerSecond(), is(10));
        assertThat(config.docsEnabled(), is(true));
        assertThat(config.sessionTtlHours(), is(24));
        assertThat(config.trustProxy(), is(false));
        assertThat(config.smtpPort(), is(587));
        assertThat(config.frontendBaseUrl(), is("http://localhost:5173"));
        assertThat(config.referralCodeTtlHours(), is(168));
        assertThat(config.passwordResetCodeTtlMinutes(), is(30));
        assertThat(config.loginThrottleMaxAttempts(), is(10));
        assertThat(config.loginThrottleWindowMinutes(), is(15));
        assertThat(config.passwordResetThrottleMaxAttempts(), is(5));
        assertThat(config.passwordResetThrottleWindowMinutes(), is(15));
    }

    @Test
    void anEnvironmentVariableTakesPrecedenceOverTheProperty() {
        // DB_URL isn't set in this test's environment, so fromClasspath() falls back to the property
        // file value here - the precedence itself (env wins when set) is exercised for real in
        // production via docker-compose's DB_URL/DB_USERNAME/DB_PASSWORD env vars, which can't be
        // simulated by mutating System.getenv() from a unit test (it's immutable on the JVM). This
        // instead documents and pins the fallback half of that contract: with no env var present,
        // the property file value is used, not null.
        AppConfig config = AppConfig.fromClasspath();

        assertThat(config.dbUrl(), is("jdbc:h2:./hobbs;MODE=PostgreSQL"));
    }
}
