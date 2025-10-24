package com.zacknetic.zoomintegration.security.redaction;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

/**
 * Service for redacting PII from logs and responses
 * 
 * Protects sensitive information like emails, phone numbers, and SSNs
 * from appearing in application logs
 */
@Service
public class PIIRedactionService {
    
    // Regex patterns for common PII
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "([a-zA-Z0-9])[a-zA-Z0-9._%+-]*@([a-zA-Z0-9])[a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}"
    );
    
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\d{3}-\\d{2}-\\d{4}"
    );
    
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}"
    );
    
    /**
     * Redact all PII from a string for safe logging
     */
    public String redactForLogging(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String redacted = text;
        
        // Redact emails: user@example.com → u***@e***.com
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("$1***@$2***.[REDACTED]");
        
        // Redact phone numbers: (555) 123-4567 → (***) ***-****
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("(***)***-****");
        
        // Redact SSN: 123-45-6789 → ***-**-****
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("***-**-****");
        
        // Redact credit cards: 1234 5678 9012 3456 → **** **** **** ****
        redacted = CREDIT_CARD_PATTERN.matcher(redacted).replaceAll("**** **** **** ****");
        
        return redacted;
    }
    
    /**
     * Redact email address for safe logging
     */
    public String redactEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        
        return EMAIL_PATTERN.matcher(email).replaceAll("$1***@$2***.[REDACTED]");
    }
    
    /**
     * Mask a token or secret, showing only first and last few characters
     */
    public String maskToken(String token) {
        if (token == null || token.length() < 20) {
            return "[REDACTED]";
        }
        
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }
    
    /**
     * Check if text contains potential PII
     */
    public boolean containsPII(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        return EMAIL_PATTERN.matcher(text).find() ||
               PHONE_PATTERN.matcher(text).find() ||
               SSN_PATTERN.matcher(text).find() ||
               CREDIT_CARD_PATTERN.matcher(text).find();
    }
}