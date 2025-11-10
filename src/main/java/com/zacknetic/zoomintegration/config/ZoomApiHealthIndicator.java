package com.zacknetic.zoomintegration.config;

import com.zacknetic.zoomintegration.zoom.auth.ZoomOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Health indicator for Zoom API connectivity.
 *
 * Production: Exposes health status at /actuator/health
 * Monitoring: Enables automated health checks and alerting
 * Fellow Standards: Fail-fast, explicit status reporting
 *
 * Usage: GET /actuator/health
 * Returns:
 * - UP: Zoom API is accessible with valid credentials
 * - DOWN: Cannot connect to Zoom API or invalid credentials
 */
@Component("zoom")
public class ZoomApiHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ZoomApiHealthIndicator.class);

    private final ZoomOAuthService oauthService;
    private final ZoomConfig zoomConfig;

    public ZoomApiHealthIndicator(ZoomOAuthService oauthService, ZoomConfig zoomConfig) {
        this.oauthService = oauthService;
        this.zoomConfig = zoomConfig;
    }

    @Override
    public Health health() {
        try {
            // Attempt to get access token - this validates credentials
            String accessToken = oauthService.getAccessToken();

            if (accessToken != null && !accessToken.isBlank()) {
                return Health.up()
                    .withDetail("status", "Connected to Zoom API")
                    .withDetail("baseUrl", zoomConfig.getApi().getBaseUrl())
                    .withDetail("accountId", maskCredential(zoomConfig.getOauth().getAccountId()))
                    .withDetail("tokenLength", accessToken.length())
                    .build();
            } else {
                return Health.down()
                    .withDetail("error", "Access token is empty")
                    .withDetail("baseUrl", zoomConfig.getApi().getBaseUrl())
                    .build();
            }

        } catch (IOException e) {
            log.error("Zoom API health check failed", e);

            String errorMessage = e.getMessage();
            boolean isAuthError = errorMessage != null &&
                (errorMessage.contains("invalid_client") ||
                 errorMessage.contains("authentication") ||
                 errorMessage.contains("401"));

            Health.Builder builder = Health.down()
                .withDetail("error", errorMessage)
                .withDetail("baseUrl", zoomConfig.getApi().getBaseUrl());

            if (isAuthError) {
                builder.withDetail("cause", "Invalid or missing Zoom API credentials")
                    .withDetail("fix", "Check ZOOM_ACCOUNT_ID, ZOOM_CLIENT_ID, and ZOOM_CLIENT_SECRET environment variables");
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Unexpected error in Zoom API health check", e);
            return Health.down()
                .withDetail("error", "Unexpected error: " + e.getMessage())
                .withDetail("type", e.getClass().getSimpleName())
                .build();
        }
    }

    /**
     * Masks credentials for health check output.
     * Security: PII redaction
     */
    private String maskCredential(String credential) {
        if (credential == null || credential.length() < 8) {
            return "***";
        }
        return credential.substring(0, 4) + "..." + credential.substring(credential.length() - 4);
    }
}
