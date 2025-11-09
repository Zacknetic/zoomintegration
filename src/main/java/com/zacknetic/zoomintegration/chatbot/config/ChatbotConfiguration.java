package com.zacknetic.zoomintegration.chatbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for chatbot engine.
 *
 * Fellow Golden Rule: No magic numbers - all configurable values externalized
 * Production: 12-Factor App compliance - externalized configuration
 * Security: Validated configuration prevents abuse
 *
 * All values can be overridden via:
 * - application.properties / application.yml
 * - Environment variables (CHATBOT_CONFIDENCE_THRESHOLD=0.7)
 * - Command line args (--chatbot.confidence.threshold=0.7)
 */
@Configuration
@ConfigurationProperties(prefix = "chatbot")
@Validated
@Data
public class ChatbotConfiguration {

    /**
     * Confidence threshold for intent classification.
     * Messages below this threshold will trigger clarification.
     *
     * Production: 0.6 balances accuracy vs user experience
     * Range: 0.0 to 1.0
     */
    @Min(0)
    @Max(1)
    private double confidenceThreshold = 0.6;

    /**
     * Confidence threshold for logging low-confidence warnings.
     * Production: Same as clarification threshold
     */
    @Min(0)
    @Max(1)
    private double lowConfidenceWarningThreshold = 0.6;

    /**
     * Maximum input message length in characters.
     * Production: Prevents abuse and protects against memory exhaustion
     *
     * Security: DoS prevention
     */
    @Positive
    @Max(10000)
    private int maxInputLength = 1000;

    /**
     * Session timeout in minutes.
     * Sessions inactive longer than this will be marked as timed out.
     *
     * Production: 30 minutes balances UX vs resource usage
     */
    @Positive
    @Max(1440) // Max 24 hours
    private int sessionTimeoutMinutes = 30;

    /**
     * Cleanup threshold for ended sessions in hours.
     * Ended sessions older than this will be removed from memory.
     *
     * Production: Prevents memory leaks from abandoned sessions
     * Memory Management: 1 hour retention for debugging/audit
     */
    @Positive
    @Max(24)
    private int endedSessionCleanupHours = 1;

    /**
     * Intent Classifier Configuration
     */
    private IntentClassifierConfig intentClassifier = new IntentClassifierConfig();

    @Data
    public static class IntentClassifierConfig {
        /**
         * Confidence score for short messages (word count <= shortMessageWordCount)
         * Production: Short messages are typically more specific, higher confidence
         */
        @Min(0)
        @Max(1)
        private double shortMessageConfidence = 0.95;

        /**
         * Word count threshold for short messages
         */
        @Positive
        private int shortMessageWordCount = 5;

        /**
         * Confidence score for medium messages (word count <= mediumMessageWordCount)
         */
        @Min(0)
        @Max(1)
        private double mediumMessageConfidence = 0.85;

        /**
         * Word count threshold for medium messages
         */
        @Positive
        private int mediumMessageWordCount = 10;

        /**
         * Confidence score for long messages (word count > mediumMessageWordCount)
         */
        @Min(0)
        @Max(1)
        private double longMessageConfidence = 0.75;

        /**
         * Maximum message length for logging (truncated if longer)
         * Security: Prevents log injection and excessive log size
         */
        @Positive
        private int maxLoggingMessageLength = 100;
    }

    /**
     * Entity Extractor Configuration
     */
    private EntityExtractorConfig entityExtractor = new EntityExtractorConfig();

    @Data
    public static class EntityExtractorConfig {
        /**
         * Maximum email address length (RFC 5321)
         * Security: Prevents abuse and validates proper email format
         */
        @Positive
        @Max(320) // RFC 5321 max length
        private int maxEmailLength = 254; // RFC 5322 recommended max

        /**
         * Year threshold for 2-digit year conversion.
         * Years < threshold become 20XX, >= threshold become 19XX
         *
         * Production: 50 means 00-49 -> 2000-2049, 50-99 -> 1950-1999
         */
        @Min(0)
        @Max(99)
        private int twoDigitYearThreshold = 50;

        /**
         * Minimum meeting duration in minutes
         * Production: 1 minute minimum
         */
        @Positive
        private int minDurationMinutes = 1;

        /**
         * Maximum meeting duration in minutes
         * Production: 24 hours (1440 minutes) maximum
         */
        @Positive
        private int maxDurationMinutes = 1440;
    }

    // Helper methods for common conversions

    /**
     * Get session timeout in seconds.
     * Production: Used by ConversationSession for timeout calculations
     */
    public long getSessionTimeoutSeconds() {
        return sessionTimeoutMinutes * 60L;
    }

    /**
     * Get ended session cleanup threshold in seconds.
     * Production: Used by ConversationContextManager for cleanup
     */
    public long getEndedSessionCleanupSeconds() {
        return endedSessionCleanupHours * 3600L;
    }

    /**
     * Get max logging message length with ellipsis offset.
     * Production: Returns truncation point for "..." suffix
     */
    public int getLoggingTruncationPoint() {
        return intentClassifier.getMaxLoggingMessageLength() - 3;
    }
}
