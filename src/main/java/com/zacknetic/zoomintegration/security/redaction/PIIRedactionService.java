package com.zacknetic.zoomintegration.security.redaction;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

/**
 * Automatically removes or masks sensitive personal information from logs.
 *
 * Nobody wants their email, phone number, or credit card showing up in plain text
 * in log files. This service finds that kind of stuff and redacts it before logging,
 * so we can debug issues without leaking user data.
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
     * Scans text for sensitive info and redacts it before logging.
     *
     * This catches emails, phone numbers, SSNs, and credit cards. The original text
     * structure stays intact so you can still debug, but the sensitive bits are masked.
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
     * Redacts just email addresses, keeping the first letters for identification.
     */
    public String redactEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        
        return EMAIL_PATTERN.matcher(email).replaceAll("$1***@$2***.[REDACTED]");
    }
    
    /**
     * Shows just the beginning and end of a token, hiding the middle.
     *
     * Useful when you need to identify which token you're looking at without
     * exposing the actual secret. Like showing "eyJhbG...3ODkwIn0" instead of
     * the full JWT token.
     */
    public String maskToken(String token) {
        if (token == null || token.length() < 20) {
            return "[REDACTED]";
        }
        
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }
    
    /**
     * Quickly checks if text has any sensitive info in it.
     *
     * Handy when you want to know if you should redact something before logging it.
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