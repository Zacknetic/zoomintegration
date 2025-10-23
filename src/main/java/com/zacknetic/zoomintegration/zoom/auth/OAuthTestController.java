package com.zacknetic.zoomintegration.zoom.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zacknetic.zoomintegration.config.ZoomConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test controller to verify OAuth integration works
 */
@RestController
@RequestMapping("/api/test")
public class OAuthTestController {
    
private final ZoomOAuthService oauthService;

    private final ZoomConfig zoomConfig;  // Add this line
    
    public OAuthTestController(ZoomOAuthService oauthService, ZoomConfig zoomConfig) {  // Add zoomConfig parameter
        this.oauthService = oauthService;
        this.zoomConfig = zoomConfig;  // Add this line
    }
    
    @GetMapping("/token")
    public Map<String, Object> testToken() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String token = oauthService.getAccessToken();
            
            // Don't expose full token in response (security best practice)
            String maskedToken = token.substring(0, 10) + "..." + 
                               token.substring(token.length() - 10);
            
            response.put("success", true);
            response.put("token_preview", maskedToken);
            response.put("token_length", token.length());
            response.put("message", "Successfully obtained Zoom access token");
            
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

        @GetMapping("/config")
    public Map<String, Object> testConfig() {
        Map<String, Object> response = new HashMap<>();
        
        // Check if config values are loaded (mask the secrets)
        String accountId = zoomConfig.getOauth().getAccountId();
        String clientId = zoomConfig.getOauth().getClientId();
        String clientSecret = zoomConfig.getOauth().getClientSecret();
        
        response.put("account_id_loaded", accountId != null && !accountId.isEmpty());
        response.put("account_id_preview", accountId != null ? accountId.substring(0, Math.min(8, accountId.length())) + "..." : "NULL");
        response.put("client_id_loaded", clientId != null && !clientId.isEmpty());
        response.put("client_id_preview", clientId != null ? clientId.substring(0, Math.min(8, clientId.length())) + "..." : "NULL");
        response.put("client_secret_loaded", clientSecret != null && !clientSecret.isEmpty());
        
        return response;
    }
}