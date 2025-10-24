package com.zacknetic.zoomintegration.zoom.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zacknetic.zoomintegration.config.ZoomConfig;
import com.zacknetic.zoomintegration.zoom.models.ZoomTokenResponse;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles getting and caching OAuth tokens for Zoom API calls.
 *
 * Zoom needs an auth token for every API call. Instead of fetching a new token
 * every single time (which would be slow and wasteful), we cache it and reuse it
 * until it's about to expire. The caching is thread-safe, so multiple requests
 * can happen at once without causing problems.
 */
@Service
public class ZoomOAuthService {

    private static final Logger log = LoggerFactory.getLogger(ZoomOAuthService.class);

    private final ZoomConfig zoomConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Thread-safe token cache using AtomicReference
    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();

    /**
     * Holds a token and its expiry time together as one immutable object.
     *
     * Being immutable means once it's created, it can't be changed. This makes it
     * safe to share between threads without worrying about race conditions.
     */
    private static class TokenCache {
        final String accessToken;
        final Instant tokenExpiry;

        TokenCache(String accessToken, Instant tokenExpiry) {
            this.accessToken = accessToken;
            this.tokenExpiry = tokenExpiry;
        }
    }
    
    public ZoomOAuthService(ZoomConfig zoomConfig, OkHttpClient httpClient) {
        this.zoomConfig = zoomConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Gets a valid Zoom API token, fetching a new one only if needed.
     *
     * First checks if we have a cached token that's still good. If so, returns it immediately.
     * If not, fetches a fresh one from Zoom. Uses locking to make sure multiple threads
     * don't all try to fetch new tokens at the same time.
     */
    public String getAccessToken() throws IOException {
        TokenCache cache = tokenCache.get();

        // Fast path: token is valid, return immediately
        if (isTokenValid(cache)) {
            log.debug("Using cached access token");
            return cache.accessToken;
        }

        // Slow path: need to refresh token
        // Use synchronized block to prevent multiple threads from refreshing simultaneously
        synchronized (this) {
            // Double-check: another thread might have refreshed while we waited
            cache = tokenCache.get();
            if (isTokenValid(cache)) {
                log.debug("Using cached access token (refreshed by another thread)");
                return cache.accessToken;
            }

            log.info("Fetching new access token from Zoom");
            return fetchNewToken();
        }
    }

    /**
     * Checks if we can still use the cached token.
     *
     * A token is considered valid if it exists and won't expire for at least 5 more minutes.
     * The 5-minute buffer gives us time to make API calls without the token expiring mid-request.
     */
    private boolean isTokenValid(TokenCache cache) {
        if (cache == null || cache.accessToken == null || cache.tokenExpiry == null) {
            return false;
        }

        // Consider token expired 5 minutes before actual expiry
        Instant now = Instant.now();
        Instant expiryWithBuffer = cache.tokenExpiry.minusSeconds(300);

        return now.isBefore(expiryWithBuffer);
    }
    
   /**
     * Actually goes to Zoom and gets a fresh access token.
     *
     * This is called when we don't have a token cached or the cached one is expired.
     * We send our client credentials to Zoom and they give us back a token we can use.
     */
    private String fetchNewToken() throws IOException {
        
        // For Server-to-Server OAuth, we need clientId:clientSecret in Base64
        String credentials = zoomConfig.getOauth().getClientId() + ":" +
                            zoomConfig.getOauth().getClientSecret();
        
        String encodedCredentials = Base64.getEncoder()
                                          .encodeToString(credentials.getBytes());
        
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "account_credentials")
                .add("account_id", zoomConfig.getOauth().getAccountId())
                .build();
        
        Request request = new Request.Builder()
                .url(zoomConfig.getOauth().getTokenUrl())
                .post(body)
                .addHeader("Authorization", "Basic " + encodedCredentials)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        
        log.info("Requesting token from: {}", zoomConfig.getOauth().getTokenUrl());
        log.debug("Using account ID: {}", zoomConfig.getOauth().getAccountId());
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "No body";
            
            if (!response.isSuccessful()) {
                log.error("Failed to get access token. Status: {}, Body: {}", 
                         response.code(), responseBody);
                throw new IOException("Failed to get access token: " + response.code() + " - " + responseBody);
            }
            
            log.debug("Token response: {}", responseBody);
            
            ZoomTokenResponse tokenResponse = objectMapper.readValue(
                responseBody,
                ZoomTokenResponse.class
            );

            // Cache the token using AtomicReference for thread-safety
            String newToken = tokenResponse.getAccessToken();
            Instant newExpiry = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
            tokenCache.set(new TokenCache(newToken, newExpiry));

            log.info("Successfully obtained access token, expires in {} seconds",
                    tokenResponse.getExpiresIn());

            return newToken;
        }
    }
}