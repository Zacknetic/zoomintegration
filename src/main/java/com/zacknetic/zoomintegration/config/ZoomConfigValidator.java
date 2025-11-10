package com.zacknetic.zoomintegration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates Zoom configuration on application startup.
 *
 * Production: Fail-fast validation to catch configuration issues early
 * Security: Detects mock/invalid credentials before API calls fail
 * Fellow Standards: Explicit error messages, production mindset
 */
@Component
public class ZoomConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ZoomConfigValidator.class);

    private final ZoomConfig zoomConfig;

    public ZoomConfigValidator(ZoomConfig zoomConfig) {
        this.zoomConfig = zoomConfig;
    }

    /**
     * Validates Zoom configuration after application startup.
     * Production: Runs after all beans are initialized
     * Fail-fast: Logs clear warnings for configuration issues
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        log.info("Validating Zoom API configuration...");

        boolean hasMockCredentials = false;
        StringBuilder warnings = new StringBuilder();

        // Check for mock account ID
        if (isMockValue(zoomConfig.getOauth().getAccountId())) {
            hasMockCredentials = true;
            warnings.append("\n  ZOOM_ACCOUNT_ID is using mock value");
        }

        // Check for mock client ID
        if (isMockValue(zoomConfig.getOauth().getClientId())) {
            hasMockCredentials = true;
            warnings.append("\n  ZOOM_CLIENT_ID is using mock value");
        }

        // Check for mock client secret
        if (isMockValue(zoomConfig.getOauth().getClientSecret())) {
            hasMockCredentials = true;
            warnings.append("\n  ZOOM_CLIENT_SECRET is using mock value");
        }

        if (hasMockCredentials) {
            log.warn("""

                ================================================================================
                WARNING: ZOOM API CONFIGURATION WARNING
                ================================================================================

                The application is using MOCK Zoom API credentials!
                {}

                All Zoom API calls will FAIL with "invalid_client" errors.

                TO FIX:
                1. Set environment variables:
                   - ZOOM_ACCOUNT_ID=<your-account-id>
                   - ZOOM_CLIENT_ID=<your-client-id>
                   - ZOOM_CLIENT_SECRET=<your-client-secret>

                2. Or update application-local.properties with real credentials

                3. Get credentials from: https://marketplace.zoom.us/
                   (Create a Server-to-Server OAuth app)

                ================================================================================
                """, warnings.toString());
        } else {
            log.info("Zoom API configuration validated successfully");
            log.info("  - Account ID: {} (length: {})",
                maskCredential(zoomConfig.getOauth().getAccountId()),
                zoomConfig.getOauth().getAccountId().length());
            log.info("  - Client ID: {} (length: {})",
                maskCredential(zoomConfig.getOauth().getClientId()),
                zoomConfig.getOauth().getClientId().length());
            log.info("  - Client Secret: *** (length: {})",
                zoomConfig.getOauth().getClientSecret().length());
        }
    }

    /**
     * Checks if a configuration value is a mock/placeholder value.
     */
    private boolean isMockValue(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String lowerValue = value.toLowerCase();
        return lowerValue.contains("mock") ||
               lowerValue.contains("test") ||
               lowerValue.contains("placeholder") ||
               lowerValue.contains("example") ||
               lowerValue.equals("your-") ||
               lowerValue.startsWith("xxx");
    }

    /**
     * Masks credentials for logging (shows first/last chars only).
     * Security: PII redaction for logs
     */
    private String maskCredential(String credential) {
        if (credential == null || credential.length() < 8) {
            return "***";
        }
        return credential.substring(0, 4) + "..." + credential.substring(credential.length() - 4);
    }
}
