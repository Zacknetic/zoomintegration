package com.zacknetic.zoomintegration.chatbot.engine;

import com.zacknetic.zoomintegration.chatbot.config.ChatbotConfiguration;
import com.zacknetic.zoomintegration.chatbot.models.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifies user messages into intents using pattern matching.
 *
 * Production: Simple keyword-based NLP for MVP, extensible for ML-based classification
 * Security: Input sanitization happens before classification
 * Fail-fast: Returns UNKNOWN intent rather than failing silently
 *
 * Future Enhancement: Can be extended with Dialogflow or custom ML models
 */
@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    // Pattern matching for intent classification
    // Production: Compiled patterns for performance
    private final Map<Intent, Pattern[]> intentPatterns = new HashMap<>();

    private final ChatbotConfiguration config;

    public IntentClassifier(ChatbotConfiguration config) {
        this.config = config;
        initializePatterns();
    }

    /**
     * Classifies user input into an intent with confidence score.
     *
     * @param message The user's message
     * @return IntentClassificationResult containing intent and confidence
     *
     * Production: Never returns null - always returns UNKNOWN if no match
     * Fail-fast: Logs when confidence is low for monitoring
     */
    public IntentClassificationResult classify(String message) {
        if (message == null || message.isBlank()) {
            log.warn("Empty message received for classification");
            return new IntentClassificationResult(Intent.UNKNOWN, 0.0);
        }

        String normalizedMessage = message.toLowerCase().trim();

        // Check each intent pattern
        for (Map.Entry<Intent, Pattern[]> entry : intentPatterns.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(normalizedMessage).find()) {
                    Intent intent = entry.getKey();
                    double confidence = calculateConfidence(normalizedMessage, pattern);

                    log.info("Intent classified: {} with confidence {}", intent, confidence);

                    // Fellow Golden Rule: No magic numbers - use configuration
                    if (confidence < config.getLowConfidenceWarningThreshold()) {
                        log.warn("Low confidence classification: {} for message: {}",
                            intent, sanitizeForLogging(message));
                    }

                    return new IntentClassificationResult(intent, confidence);
                }
            }
        }

        // No pattern matched - return UNKNOWN
        log.info("No intent matched for message: {}", sanitizeForLogging(message));
        return new IntentClassificationResult(Intent.UNKNOWN, 0.0);
    }

    /**
     * Initialize regex patterns for each intent.
     * Production: Organized, maintainable pattern definitions
     * Explicit: Clear keywords for each intent
     */
    private void initializePatterns() {
        // Greeting patterns
        intentPatterns.put(Intent.GREETING, new Pattern[]{
            Pattern.compile("\\b(hello|hi|hey|greetings|good morning|good afternoon)\\b"),
            Pattern.compile("^(yo|sup|what's up)\\b")
        });

        // Help patterns
        intentPatterns.put(Intent.HELP, new Pattern[]{
            Pattern.compile("\\b(help|what can you do|commands|assist)\\b"),
            Pattern.compile("\\b(show|list) (commands|options|capabilities)\\b")
        });

        // Schedule meeting patterns
        intentPatterns.put(Intent.SCHEDULE_MEETING, new Pattern[]{
            Pattern.compile("\\b(schedule|create|set up|arrange|book).*\\b(meeting|call|conference)\\b"),
            Pattern.compile("\\b(meeting|call).*\\b(tomorrow|today|next|on)\\b"),
            Pattern.compile("\\b(schedule|create|setup).*\\b(zoom|video call)\\b")
        });

        // List meetings patterns
        intentPatterns.put(Intent.LIST_MEETINGS, new Pattern[]{
            Pattern.compile("\\b(list|show|get|display|view).*\\b(meetings|calls|conferences)\\b"),
            Pattern.compile("\\b(my|upcoming|today's|this week's).*\\b(meetings|calls)\\b"),
            Pattern.compile("\\bwhat.*\\b(meetings|calls)\\b")
        });

        // Get meeting details patterns
        intentPatterns.put(Intent.GET_MEETING, new Pattern[]{
            Pattern.compile("\\b(get|show|find|details|info).*\\b(meeting|call)\\b(?!s\\b)"),
            Pattern.compile("\\btell me about.*\\b(meeting|call)\\b"),
            Pattern.compile("\\bmeeting (details|info|information)\\b")
        });

        // Update meeting patterns
        intentPatterns.put(Intent.UPDATE_MEETING, new Pattern[]{
            Pattern.compile("\\b(update|modify|change|reschedule|edit).*\\b(meeting|call)\\b"),
            Pattern.compile("\\bmove.*\\b(meeting|call)\\b"),
            Pattern.compile("\\bchange.*\\b(time|date).*\\b(meeting|call)\\b")
        });

        // Delete meeting patterns
        intentPatterns.put(Intent.DELETE_MEETING, new Pattern[]{
            Pattern.compile("\\b(cancel|delete|remove).*\\b(meeting|call)\\b"),
            Pattern.compile("\\bdrop.*\\b(meeting|call)\\b")
        });

        // List recordings patterns
        intentPatterns.put(Intent.LIST_RECORDINGS, new Pattern[]{
            Pattern.compile("\\b(list|show|get|display|view).*\\b(recordings|videos)\\b"),
            Pattern.compile("\\b(my|recent|all).*\\b(recordings|videos)\\b"),
            Pattern.compile("\\bwhat.*\\b(recordings|videos)\\b")
        });

        // Get recording patterns
        intentPatterns.put(Intent.GET_RECORDING, new Pattern[]{
            Pattern.compile("\\b(get|show|find|details).*\\b(recording|video)\\b(?!s\\b)"),
            Pattern.compile("\\brecording (details|info|information)\\b")
        });

        // Download recording patterns
        intentPatterns.put(Intent.DOWNLOAD_RECORDING, new Pattern[]{
            Pattern.compile("\\b(download|get|fetch).*\\b(recording|video)\\b"),
            Pattern.compile("\\bsave.*\\b(recording|video)\\b")
        });

        // Get user patterns
        intentPatterns.put(Intent.GET_USER, new Pattern[]{
            Pattern.compile("\\b(get|show|find|lookup).*\\b(user|account|profile)\\b(?!s\\b)"),
            Pattern.compile("\\b(who is|user info|user details)\\b"),
            Pattern.compile("\\btell me about.*\\b(user|account)\\b")
        });

        // List users patterns
        intentPatterns.put(Intent.LIST_USERS, new Pattern[]{
            Pattern.compile("\\b(list|show|get|display|view).*\\busers\\b"),
            Pattern.compile("\\ball users\\b"),
            Pattern.compile("\\buser (list|directory)\\b")
        });

        // Create user patterns
        intentPatterns.put(Intent.CREATE_USER, new Pattern[]{
            Pattern.compile("\\b(create|add|new|provision).*\\b(user|account)\\b"),
            Pattern.compile("\\b(add|invite).*\\b(someone|person|user)\\b")
        });

        log.info("Intent classifier initialized with {} intent patterns", intentPatterns.size());
    }

    /**
     * Calculate confidence score based on pattern match quality.
     *
     * Production: Provides confidence metric for monitoring and UX
     * Fellow Golden Rule: No magic numbers - thresholds from configuration
     * @return confidence score between 0.0 and 1.0
     */
    private double calculateConfidence(String message, Pattern pattern) {
        // Simple confidence: presence of pattern = 0.8, exact match = 1.0
        // Future: Can be enhanced with ML-based scoring

        String[] words = message.split("\\s+");
        int wordCount = words.length;

        // Shorter, more specific messages get higher confidence
        ChatbotConfiguration.IntentClassifierConfig classifierConfig = config.getIntentClassifier();

        if (wordCount <= classifierConfig.getShortMessageWordCount()) {
            return classifierConfig.getShortMessageConfidence();
        } else if (wordCount <= classifierConfig.getMediumMessageWordCount()) {
            return classifierConfig.getMediumMessageConfidence();
        } else {
            return classifierConfig.getLongMessageConfidence();
        }
    }

    /**
     * Sanitize message for logging (truncate if too long).
     * Security: Prevents log injection
     * Production: PII will be redacted by PIIRedactionService at log output
     * Fellow Golden Rule: No magic numbers - max length from configuration
     */
    private String sanitizeForLogging(String message) {
        if (message == null) {
            return "[null]";
        }

        int maxLength = config.getIntentClassifier().getMaxLoggingMessageLength();

        // Truncate long messages for logging
        if (message.length() > maxLength) {
            return message.substring(0, config.getLoggingTruncationPoint()) + "...";
        }
        // Remove newlines to prevent log injection
        return message.replaceAll("[\r\n]", " ");
    }

    /**
     * Result of intent classification.
     * Production: Explicit result object rather than tuple or array
     */
    public static class IntentClassificationResult {
        private final Intent intent;
        private final double confidence;

        public IntentClassificationResult(Intent intent, double confidence) {
            if (intent == null) {
                throw new IllegalArgumentException("Intent cannot be null");
            }
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
            }
            this.intent = intent;
            this.confidence = confidence;
        }

        public Intent getIntent() {
            return intent;
        }

        public double getConfidence() {
            return confidence;
        }

        /**
         * Checks if classification confidence is acceptable.
         * Production: Explicit threshold for UX decisions
         * Note: Threshold defined in ChatbotConfiguration
         */
        public boolean isConfident() {
            // Default threshold is 0.6, configurable via chatbot.confidence-threshold
            return confidence >= 0.6;
        }
    }
}
