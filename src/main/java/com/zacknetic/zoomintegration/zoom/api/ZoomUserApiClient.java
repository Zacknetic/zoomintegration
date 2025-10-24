package com.zacknetic.zoomintegration.zoom.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zacknetic.zoomintegration.config.ZoomConfig;
import com.zacknetic.zoomintegration.security.redaction.PIIRedactionService;
import com.zacknetic.zoomintegration.zoom.auth.ZoomOAuthService;
import com.zacknetic.zoomintegration.zoom.models.ZoomUser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Client for Zoom User API endpoints
 * 
 * Demonstrates proper API authentication and PII protection in logs
 */
@Service
public class ZoomUserApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(ZoomUserApiClient.class);
    
    private final ZoomConfig zoomConfig;
    private final ZoomOAuthService oauthService;
    private final PIIRedactionService redactionService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public ZoomUserApiClient(
            ZoomConfig zoomConfig,
            ZoomOAuthService oauthService,
            PIIRedactionService redactionService) {
        this.zoomConfig = zoomConfig;
        this.oauthService = oauthService;
        this.redactionService = redactionService;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Get the current authenticated user's information
     */
    public ZoomUser getCurrentUser() throws IOException {
        String accessToken = oauthService.getAccessToken();
        String url = zoomConfig.getApi().getBaseUrl() + "/users/me";
        
        log.info("Fetching current user info from Zoom API");
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                // Redact response before logging (might contain PII)
                String redactedResponse = redactionService.redactForLogging(responseBody);
                log.error("Failed to get user info. Status: {}, Body: {}", 
                         response.code(), redactedResponse);
                throw new IOException("Failed to get user info: " + response.code());
            }
            
            ZoomUser user = objectMapper.readValue(responseBody, ZoomUser.class);
            
            // Log success but redact the email
            log.info("Successfully retrieved user: {} (email: {})", 
                    user.getFirstName() + " " + user.getLastName(),
                    redactionService.redactEmail(user.getEmail()));
            
            return user;
        }
    }
    
    /**
     * Get a specific user by ID or email
     */
    public ZoomUser getUser(String userId) throws IOException {
        String accessToken = oauthService.getAccessToken();
        String url = zoomConfig.getApi().getBaseUrl() + "/users/" + userId;
        
        // Redact userId if it looks like an email before logging
        String logSafeUserId = redactionService.containsPII(userId) 
            ? redactionService.redactEmail(userId) 
            : userId;
        
        log.info("Fetching user info for: {}", logSafeUserId);
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                String redactedResponse = redactionService.redactForLogging(responseBody);
                log.error("Failed to get user info. Status: {}, Body: {}", 
                         response.code(), redactedResponse);
                throw new IOException("Failed to get user info: " + response.code());
            }
            
            ZoomUser user = objectMapper.readValue(responseBody, ZoomUser.class);
            
            log.info("Successfully retrieved user: {}", user.getId());
            
            return user;
        }
    }
}