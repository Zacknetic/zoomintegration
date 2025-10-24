package com.zacknetic.zoomintegration.security.redaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for PIIRedactionService
 *
 * Tests verify that PII is properly redacted from logs while maintaining
 * enough context for debugging
 */
class PIIRedactionServiceTest {

    private PIIRedactionService redactionService;

    @BeforeEach
    void setUp() {
        redactionService = new PIIRedactionService();
    }

    @Test
    void redactForLogging_shouldRedactEmail() {
        String input = "User email: test@example.com logged in";
        String result = redactionService.redactForLogging(input);

        assertThat(result).doesNotContain("test@example.com");
        assertThat(result).contains("t***@e***.[REDACTED]");
    }

    @Test
    void redactForLogging_shouldRedactMultipleEmails() {
        String input = "Sent from alice@company.com to bob@company.com";
        String result = redactionService.redactForLogging(input);

        assertThat(result).doesNotContain("alice@company.com");
        assertThat(result).doesNotContain("bob@company.com");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    void redactForLogging_shouldRedactPhoneNumber() {
        String input = "Contact: (555) 123-4567";
        String result = redactionService.redactForLogging(input);

        assertThat(result).doesNotContain("555");
        assertThat(result).doesNotContain("123-4567");
        assertThat(result).contains("(***)***-****");
    }

    @Test
    void redactForLogging_shouldRedactSSN() {
        String input = "SSN: 123-45-6789";
        String result = redactionService.redactForLogging(input);

        assertThat(result).doesNotContain("123-45-6789");
        assertThat(result).contains("***-**-****");
    }

    @Test
    void redactForLogging_shouldRedactCreditCard() {
        String input = "Card: 1234 5678 9012 3456";
        String result = redactionService.redactForLogging(input);

        assertThat(result).doesNotContain("1234 5678 9012 3456");
        assertThat(result).contains("**** **** **** ****");
    }

    @Test
    void redactForLogging_shouldHandleNullInput() {
        String result = redactionService.redactForLogging(null);
        assertThat(result).isNull();
    }

    @Test
    void redactForLogging_shouldHandleEmptyString() {
        String result = redactionService.redactForLogging("");
        assertThat(result).isEmpty();
    }

    @Test
    void redactForLogging_shouldRedactMultiplePIITypes() {
        String input = "User test@example.com, phone (555) 123-4567, SSN 123-45-6789";
        String result = redactionService.redactForLogging(input);

        assertThat(result).doesNotContain("test@example.com");
        assertThat(result).doesNotContain("555");
        assertThat(result).doesNotContain("123-45-6789");
    }

    @Test
    void redactEmail_shouldRedactEmailCorrectly() {
        String email = "john.doe@company.com";
        String result = redactionService.redactEmail(email);

        assertThat(result).doesNotContain("john.doe");
        assertThat(result).contains("j***@c***.[REDACTED]");
    }

    @Test
    void redactEmail_shouldHandleNullEmail() {
        String result = redactionService.redactEmail(null);
        assertThat(result).isNull();
    }

    @Test
    void redactEmail_shouldHandleEmptyEmail() {
        String result = redactionService.redactEmail("");
        assertThat(result).isEmpty();
    }

    @Test
    void maskToken_shouldMaskLongToken() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0";
        String result = redactionService.maskToken(token);

        assertThat(result).startsWith("eyJhbGciOi");
        assertThat(result).endsWith("TY3ODkwIn0");
        assertThat(result).contains("...");
        assertThat(result.length()).isLessThan(token.length());
    }

    @Test
    void maskToken_shouldFullyRedactShortToken() {
        String shortToken = "short";
        String result = redactionService.maskToken(shortToken);

        assertThat(result).isEqualTo("[REDACTED]");
    }

    @Test
    void maskToken_shouldHandleNullToken() {
        String result = redactionService.maskToken(null);
        assertThat(result).isEqualTo("[REDACTED]");
    }

    @Test
    void containsPII_shouldReturnTrueForEmail() {
        String text = "Contact me at test@example.com";
        assertThat(redactionService.containsPII(text)).isTrue();
    }

    @Test
    void containsPII_shouldReturnTrueForPhone() {
        String text = "Call (555) 123-4567";
        assertThat(redactionService.containsPII(text)).isTrue();
    }

    @Test
    void containsPII_shouldReturnTrueForSSN() {
        String text = "SSN: 123-45-6789";
        assertThat(redactionService.containsPII(text)).isTrue();
    }

    @Test
    void containsPII_shouldReturnTrueForCreditCard() {
        String text = "Card: 1234 5678 9012 3456";
        assertThat(redactionService.containsPII(text)).isTrue();
    }

    @Test
    void containsPII_shouldReturnFalseForNoPII() {
        String text = "This is a normal log message";
        assertThat(redactionService.containsPII(text)).isFalse();
    }

    @Test
    void containsPII_shouldReturnFalseForNull() {
        assertThat(redactionService.containsPII(null)).isFalse();
    }

    @Test
    void containsPII_shouldReturnFalseForEmptyString() {
        assertThat(redactionService.containsPII("")).isFalse();
    }

    @Test
    void redactForLogging_shouldPreserveNonPIIContent() {
        String input = "User test@example.com logged in successfully at 2024-01-15T10:30:00Z";
        String result = redactionService.redactForLogging(input);

        assertThat(result).contains("logged in successfully");
        assertThat(result).contains("2024-01-15T10:30:00Z");
        assertThat(result).doesNotContain("test@example.com");
    }
}
