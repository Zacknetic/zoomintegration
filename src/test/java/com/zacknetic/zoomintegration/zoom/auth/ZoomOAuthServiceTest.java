package com.zacknetic.zoomintegration.zoom.auth;

import com.zacknetic.zoomintegration.config.ZoomConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for ZoomOAuthService
 *
 * Uses MockWebServer to test OAuth flow without hitting real Zoom API
 */
class ZoomOAuthServiceTest {

    private MockWebServer mockWebServer;
    private ZoomOAuthService oauthService;
    private ZoomConfig zoomConfig;
    private okhttp3.OkHttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        zoomConfig = new ZoomConfig();
        zoomConfig.setOauth(new ZoomConfig.OAuth());
        zoomConfig.getOauth().setTokenUrl(mockWebServer.url("/oauth/token").toString());
        zoomConfig.getOauth().setAccountId("test-account-id");
        zoomConfig.getOauth().setClientId("test-client-id");
        zoomConfig.getOauth().setClientSecret("test-client-secret");

        httpClient = new okhttp3.OkHttpClient();
        oauthService = new ZoomOAuthService(zoomConfig, httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getAccessToken_shouldFetchNewTokenOnFirstCall() throws IOException, InterruptedException {
        // Arrange
        String mockTokenResponse = """
            {
                "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
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
        assertThat(token).isEqualTo("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/oauth/token");

        // Verify Authorization header contains Base64 encoded credentials
        String authHeader = request.getHeader("Authorization");
        assertThat(authHeader).startsWith("Basic ");

        String encodedCredentials = authHeader.substring(6);
        String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials));
        assertThat(decodedCredentials).isEqualTo("test-client-id:test-client-secret");

        // Verify request body
        String body = request.getBody().readUtf8();
        assertThat(body).contains("grant_type=account_credentials");
        assertThat(body).contains("account_id=test-account-id");
    }

    @Test
    void getAccessToken_shouldReuseValidCachedToken() throws IOException {
        // Arrange
        String mockTokenResponse = """
            {
                "access_token": "cached-token",
                "token_type": "bearer",
                "expires_in": 3600
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(mockTokenResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // Act - First call fetches token
        String firstToken = oauthService.getAccessToken();

        // Act - Second call should use cached token (no new request)
        String secondToken = oauthService.getAccessToken();

        // Assert
        assertThat(firstToken).isEqualTo(secondToken);
        assertThat(firstToken).isEqualTo("cached-token");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1); // Only one request made
    }

    @Test
    void getAccessToken_shouldRefreshExpiredToken() throws IOException, InterruptedException {
        // Arrange - First token with very short expiry
        String firstTokenResponse = """
            {
                "access_token": "first-token",
                "token_type": "bearer",
                "expires_in": 1
            }
            """;

        String secondTokenResponse = """
            {
                "access_token": "second-token",
                "token_type": "bearer",
                "expires_in": 3600
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(firstTokenResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
            .setBody(secondTokenResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // Act - First call
        String firstToken = oauthService.getAccessToken();
        assertThat(firstToken).isEqualTo("first-token");

        // Wait for token to expire (expires_in: 1 second + 5 minute buffer is considered expired immediately)
        Thread.sleep(100);

        // Act - Second call should fetch new token
        String secondToken = oauthService.getAccessToken();

        // Assert
        assertThat(secondToken).isEqualTo("second-token");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void getAccessToken_shouldThrowIOExceptionOn401Unauthorized() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("{\"reason\":\"Invalid credentials\"}"));

        // Act & Assert
        assertThatThrownBy(() -> oauthService.getAccessToken())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Failed to get access token: 401");
    }

    @Test
    void getAccessToken_shouldThrowIOExceptionOn500ServerError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        // Act & Assert
        assertThatThrownBy(() -> oauthService.getAccessToken())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Failed to get access token: 500");
    }

    @Test
    void getAccessToken_shouldThrowIOExceptionOnInvalidJSON() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("This is not valid JSON"));

        // Act & Assert
        assertThatThrownBy(() -> oauthService.getAccessToken())
            .isInstanceOf(IOException.class);
    }

    @Test
    void getAccessToken_shouldHandleNetworkFailure() throws IOException {
        // Arrange - Shutdown server to simulate network failure
        mockWebServer.shutdown();

        // Act & Assert
        assertThatThrownBy(() -> oauthService.getAccessToken())
            .isInstanceOf(IOException.class);
    }

    @Test
    void getAccessToken_shouldSetCorrectContentTypeHeader() throws IOException, InterruptedException {
        // Arrange
        String mockTokenResponse = """
            {
                "access_token": "test-token",
                "token_type": "bearer",
                "expires_in": 3600
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(mockTokenResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // Act
        oauthService.getAccessToken();

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Content-Type"))
            .isEqualTo("application/x-www-form-urlencoded");
    }
}
