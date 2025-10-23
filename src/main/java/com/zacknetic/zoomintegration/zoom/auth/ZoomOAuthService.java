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

/**
 * Service for handling Zoom Server-to-Server OAuth authentication
 */
@Service
public class ZoomOAuthService {
    
    private static final Logger log = LoggerFactory.getLogger(ZoomOAuthService.class);
    
    private final ZoomConfig zoomConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    // Cached token
    private String accessToken;
    private Instant tokenExpiry;
    
    public ZoomOAuthService(ZoomConfig zoomConfig) {
        this.zoomConfig = zoomConfig;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Get a valid access token, refreshing if necessary
     */
    public String getAccessToken() throws IOException {
        if (isTokenValid()) {
            log.debug("Using cached access token");
            return accessToken;
        }
        
        log.info("Fetching new access token from Zoom");
        return fetchNewToken();
    }
    
    /**
     * Check if current token is still valid
     */
    private boolean isTokenValid() {
        if (accessToken == null || tokenExpiry == null) {
            return false;
        }
        
        // Consider token expired 5 minutes before actual expiry
        Instant now = Instant.now();
        Instant expiryWithBuffer = tokenExpiry.minusSeconds(300);
        
        return now.isBefore(expiryWithBuffer);
    }
    
   /**
     * Fetch a new access token from Zoom OAuth endpoint
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
            
            // Cache the token
            this.accessToken = tokenResponse.getAccessToken();
            this.tokenExpiry = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
            
            log.info("Successfully obtained access token, expires in {} seconds", 
                    tokenResponse.getExpiresIn());
            
            return this.accessToken;
        }
    }
}