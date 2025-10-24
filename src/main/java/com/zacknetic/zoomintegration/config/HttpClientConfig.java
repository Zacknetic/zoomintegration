package com.zacknetic.zoomintegration.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Sets up a shared HTTP client for the entire application.
 *
 * Instead of creating a new HTTP client every time we need to call an API, we create
 * one here and share it everywhere. This is more efficient because the client maintains
 * a pool of reusable connections and handles retries intelligently.
 */
@Configuration
public class HttpClientConfig {

    /**
     * Creates one HTTP client that everyone in the app can use.
     *
     * This is way better than making a new client every time because:
     * - Connections get reused, which is faster
     * - We don't waste memory creating tons of clients
     * - Thread pools are shared efficiently
     *
     * The timeouts here (10s to connect, 30s to read/write) are reasonable defaults
     * but can be adjusted if Zoom's API is consistently slower.
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }
}
