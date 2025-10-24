package com.zacknetic.zoomintegration.config;

import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cleans up resources when the application shuts down.
 *
 * When Spring Boot shuts down (like when you hit Ctrl+C or deploy a new version),
 * we want to finish what we're doing and close connections nicely instead of just
 * abruptly dying. This class makes sure we close HTTP connections, finish pending
 * requests, and generally clean up after ourselves.
 */
@Component
public class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private final OkHttpClient httpClient;

    public GracefulShutdown(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Runs automatically when Spring Boot is shutting down.
     *
     * Spring calls this method thanks to the @PreDestroy annotation, giving us a chance
     * to clean up connections and finish pending work before the app fully exits.
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Graceful shutdown initiated - cleaning up resources");

        try {
            // Shutdown HTTP client executor service
            httpClient.dispatcher().executorService().shutdown();
            log.info("HTTP client dispatcher shut down");

            // Evict all connections from the connection pool
            httpClient.connectionPool().evictAll();
            log.info("HTTP connection pool evicted");

            // Close the cache if present
            if (httpClient.cache() != null) {
                httpClient.cache().close();
                log.info("HTTP cache closed");
            }

            log.info("Graceful shutdown completed successfully");

        } catch (Exception e) {
            log.error("Error during graceful shutdown", e);
        }
    }
}
