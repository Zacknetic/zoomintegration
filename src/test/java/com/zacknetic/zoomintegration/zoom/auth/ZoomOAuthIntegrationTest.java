package com.zacknetic.zoomintegration.zoom.auth;

import com.zacknetic.zoomintegration.config.ZoomConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Zoom OAuth flow
 *
 * Tests the complete OAuth authentication flow with a real Spring context
 * using MockWebServer to simulate Zoom API
 */
@SpringBootTest
class ZoomOAuthIntegrationTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private ZoomOAuthService oauthService;

    @Autowired
    private ZoomConfig zoomConfig;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("zoom.oauth.token-url", () -> "http://localhost:" + mockWebServer.getPort() + "/oauth/token");
        registry.add("zoom.oauth.account-id", () -> "test-account");
        registry.add("zoom.oauth.client-id", () -> "test-client");
        registry.add("zoom.oauth.client-secret", () -> "test-secret");
    }

    @Test
    void fullOAuthFlowWithSpringContext() throws IOException {
        // Arrange
        String mockTokenResponse = """
            {
                "access_token": "integration-test-token",
                "token_type": "bearer",
                "expires_in": 3600
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(mockTokenResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // Act
        String token = oauthService.getAccessToken();

        // Assert
        assertThat(token).isEqualTo("integration-test-token");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void configurationIsLoadedCorrectly() {
        // Verify Spring loaded our test configuration
        assertThat(zoomConfig.getOauth().getAccountId()).isEqualTo("test-account");
        assertThat(zoomConfig.getOauth().getClientId()).isEqualTo("test-client");
        assertThat(zoomConfig.getOauth().getClientSecret()).isEqualTo("test-secret");
    }
}
