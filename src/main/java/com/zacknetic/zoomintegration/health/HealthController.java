package com.zacknetic.zoomintegration.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoints for monitoring and deployment verification.
 *
 * These endpoints let you quickly check if the app is running and which version
 * is deployed. Really useful when you deploy a new version and want to verify
 * that the right build made it to production.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @Value("${app.build.id:unknown}")
    private String buildId;

    @Value("${app.version:0.0.1-SNAPSHOT}")
    private String version;

    @Value("${spring.application.name:zoom-integration}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Main health check that tells you everything about the running app.
     *
     * Hit this endpoint after deploying to verify you got the right version up.
     * Also useful for load balancers and monitoring tools to check if the app
     * is alive and what version is running.
     */
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("buildId", buildId);
        health.put("version", version);
        health.put("application", applicationName);
        health.put("profile", activeProfile);
        health.put("timestamp", Instant.now().toString());

        return health;
    }

    /**
     * Quick "are you alive?" check for Kubernetes.
     *
     * Returns immediately with a simple response. Kubernetes hits this regularly
     * to make sure the app hasn't frozen or crashed.
     */
    @GetMapping("/live")
    public Map<String, String> liveness() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return response;
    }

    /**
     * Checks if the app is ready to handle traffic.
     *
     * Right now this just says "READY", but you could add checks here for things like
     * "can we connect to the database?" or "is Zoom's API responding?" before telling
     * Kubernetes to send traffic our way.
     */
    @GetMapping("/ready")
    public Map<String, String> readiness() {
        Map<String, String> response = new HashMap<>();
        // TODO: Add checks for database connection, Zoom API availability, etc.
        response.put("status", "READY");
        return response;
    }
}
