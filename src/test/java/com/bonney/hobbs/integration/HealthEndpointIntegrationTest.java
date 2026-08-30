package com.bonney.hobbs.integration;

import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HealthEndpointIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthReturnsUp() {
        assertThat(createClient().health().getStatus(), is("UP"));
    }

    @Test
    void versionEndpointDoesNotRequireAuthentication() {
        assertThat(createClient().version().getSha(), is(notNullValue()));
    }

    @Test
    void openApiDocumentationDoesNotRequireAuthenticationWhenEnabled() throws Exception {
        Request request = new Request.Builder()
                .url("http://localhost:" + application.getPort() + "/openapi")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code(), is(200));
        }
    }
}
