package com.zacknetic.zoomintegration.zoom.api;

import com.zacknetic.zoomintegration.security.redaction.PIIRedactionService;
import com.zacknetic.zoomintegration.zoom.models.ZoomUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for Zoom User API and PII redaction
 */
@RestController
@RequestMapping("/api/test")
public class UserApiTestController {
    
    private final ZoomUserApiClient userApiClient;
    private final PIIRedactionService redactionService;
    
    public UserApiTestController(
            ZoomUserApiClient userApiClient,
            PIIRedactionService redactionService) {
        this.userApiClient = userApiClient;
        this.redactionService = redactionService;
    }
    
    @GetMapping("/user")
    public Map<String, Object> testGetUser() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            ZoomUser user = userApiClient.getCurrentUser();
            
            response.put("success", true);
            response.put("user_id", user.getId());
            response.put("name", user.getFirstName() + " " + user.getLastName());
            response.put("email_redacted", redactionService.redactEmail(user.getEmail()));
            response.put("department", user.getDepartment());
            response.put("status", user.getStatus());
            response.put("message", "Successfully retrieved user info with PII protection");
            
            // Note: We return redacted email to browser, real email never exposed
            
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }
    
    @GetMapping("/redaction")
    public Map<String, Object> testRedaction() {
        Map<String, Object> response = new HashMap<>();
        
        // Test PII redaction with sample data
        String sampleText = "Contact John Doe at john.doe@example.com or call (555) 123-4567. SSN: 123-45-6789";
        
        response.put("original_length", sampleText.length());
        response.put("redacted", redactionService.redactForLogging(sampleText));
        response.put("contains_pii", redactionService.containsPII(sampleText));
        response.put("message", "PII redaction service working correctly");
        
        return response;
    }
}