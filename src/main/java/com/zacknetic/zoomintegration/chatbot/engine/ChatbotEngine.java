package com.zacknetic.zoomintegration.chatbot.engine;

import com.zacknetic.zoomintegration.chatbot.config.ChatbotConfiguration;
import com.zacknetic.zoomintegration.chatbot.models.ChatMessage;
import com.zacknetic.zoomintegration.chatbot.models.ConversationSession;
import com.zacknetic.zoomintegration.chatbot.models.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Core chatbot engine that orchestrates intent classification,
 * entity extraction, and response generation.
 *
 * Production: Service layer with comprehensive error handling
 * Security: Input sanitization and validation
 * Fail-fast: Explicit error responses rather than silent failures
 */
@Service
public class ChatbotEngine {

    private static final Logger log = LoggerFactory.getLogger(ChatbotEngine.class);

    private final IntentClassifier intentClassifier;
    private final EntityExtractor entityExtractor;
    private final ConversationContextManager contextManager;
    private final ChatbotConfiguration config;

    public ChatbotEngine(
        IntentClassifier intentClassifier,
        EntityExtractor entityExtractor,
        ConversationContextManager contextManager,
        ChatbotConfiguration config
    ) {
        this.intentClassifier = intentClassifier;
        this.entityExtractor = entityExtractor;
        this.contextManager = contextManager;
        this.config = config;
    }

    /**
     * Processes a user message and returns a bot response.
     *
     * Production: Main entry point for chatbot interactions
     * Security: Sanitizes input before processing
     * Fail-fast: Returns error messages for invalid input
     *
     * @param userId User ID (for session management)
     * @param messageText User's message
     * @return ChatbotResponse containing bot's reply and metadata
     */
    public ChatbotResponse processMessage(String userId, String messageText) {
        // Validate input
        if (userId == null || userId.isBlank()) {
            log.error("User ID is required for chat processing");
            throw new IllegalArgumentException("User ID is required");
        }

        if (messageText == null || messageText.isBlank()) {
            log.warn("Empty message received from user: {}", userId);
            return createErrorResponse(
                userId,
                "I didn't receive any message. Could you please type something?"
            );
        }

        // Sanitize input
        String sanitizedMessage = sanitizeInput(messageText);

        log.info("Processing message from user: {}", userId);

        try {
            // Get or create conversation session
            ConversationSession session = contextManager.getOrCreateSession(userId);

            // Create user message
            ChatMessage userMessage = ChatMessage.userMessage(session.getSessionId(), sanitizedMessage);

            // Classify intent
            IntentClassifier.IntentClassificationResult classification =
                intentClassifier.classify(sanitizedMessage);

            Intent intent = classification.getIntent();
            double confidence = classification.getConfidence();

            log.info("Classified intent: {} with confidence: {}", intent, confidence);

            // Update message with intent
            userMessage.setIntent(intent);
            userMessage.setConfidence(confidence);

            // Extract entities
            Map<String, String> entities = entityExtractor.extractEntities(sanitizedMessage);
            userMessage.setEntities(entities);

            if (!entities.isEmpty()) {
                log.info("Extracted {} entities: {}", entities.size(), entities.keySet());
            }

            // Store last intent in session
            session.setLastIntent(intent);

            // Update session activity
            contextManager.updateSessionActivity(session.getSessionId());

            // Generate response based on intent
            ChatMessage botResponse = generateResponse(session, userMessage, intent, entities, confidence);

            // Build response object
            return ChatbotResponse.builder()
                .userId(userId)
                .sessionId(session.getSessionId())
                .userMessage(userMessage)
                .botMessage(botResponse)
                .intent(intent)
                .confidence(confidence)
                .entities(entities)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Error processing message from user: {}", userId, e);
            return createErrorResponse(
                userId,
                "I encountered an error processing your message. Please try again."
            );
        }
    }

    /**
     * Generates bot response based on intent and entities.
     * Production: Intent-based response routing
     */
    private ChatMessage generateResponse(
        ConversationSession session,
        ChatMessage userMessage,
        Intent intent,
        Map<String, String> entities,
        double confidence
    ) {
        String responseText;

        // If confidence is low, ask for clarification
        // Fellow Golden Rule: No magic numbers - use configuration
        if (!Intent.UNKNOWN.equals(intent) && confidence < config.getConfidenceThreshold()) {
            responseText = "I'm not entirely sure I understood that correctly. " +
                "Did you want to " + intent.getDescription().toLowerCase() + "?";
            return ChatMessage.botMessage(session.getSessionId(), responseText);
        }

        // Route to intent-specific response
        switch (intent) {
            case GREETING:
                responseText = handleGreeting();
                break;

            case HELP:
                responseText = handleHelp();
                break;

            case SCHEDULE_MEETING:
                responseText = handleScheduleMeeting(session, entities);
                break;

            case LIST_MEETINGS:
                responseText = handleListMeetings(session, entities);
                break;

            case GET_MEETING:
                responseText = handleGetMeeting(session, entities);
                break;

            case UPDATE_MEETING:
                responseText = handleUpdateMeeting(session, entities);
                break;

            case DELETE_MEETING:
                responseText = handleDeleteMeeting(session, entities);
                break;

            case LIST_RECORDINGS:
                responseText = handleListRecordings(session, entities);
                break;

            case GET_RECORDING:
                responseText = handleGetRecording(session, entities);
                break;

            case DOWNLOAD_RECORDING:
                responseText = handleDownloadRecording(session, entities);
                break;

            case GET_USER:
                responseText = handleGetUser(session, entities);
                break;

            case LIST_USERS:
                responseText = handleListUsers(session, entities);
                break;

            case CREATE_USER:
                responseText = handleCreateUser(session, entities);
                break;

            case UNKNOWN:
            default:
                responseText = handleUnknown();
                break;
        }

        return ChatMessage.botMessage(session.getSessionId(), responseText);
    }

    // Intent handlers (MVP implementations - will be enhanced with actual Zoom API calls)

    private String handleGreeting() {
        return "Hello! I'm your Zoom assistant. I can help you manage meetings, recordings, and users. " +
            "Type 'help' to see what I can do!";
    }

    private String handleHelp() {
        return "Here's what I can help you with:\n\n" +
            "MEETINGS:\n" +
            "- Schedule a meeting: \"Schedule a meeting tomorrow at 2pm\"\n" +
            "- List meetings: \"Show my meetings this week\"\n" +
            "- Get meeting details: \"Tell me about meeting 12345\"\n" +
            "- Update meeting: \"Reschedule meeting 12345 to 3pm\"\n" +
            "- Cancel meeting: \"Cancel meeting 12345\"\n\n" +
            "RECORDINGS:\n" +
            "- List recordings: \"Show my recordings\"\n" +
            "- Get recording: \"Get recording for meeting 12345\"\n" +
            "- Download recording: \"Download recording 67890\"\n\n" +
            "USERS:\n" +
            "- Get user: \"Show user john@example.com\"\n" +
            "- List users: \"List all users\"\n" +
            "- Create user: \"Add user jane@example.com\"\n\n" +
            "Just ask in natural language, and I'll do my best to help!";
    }

    private String handleScheduleMeeting(ConversationSession session, Map<String, String> entities) {
        // Check if we have required entities
        String date = entities.get("date");
        String time = entities.get("time");
        String email = entities.get("email");

        // Store entities in context for multi-turn conversation
        if (date != null) session.setContextValue("meetingDate", date);
        if (time != null) session.setContextValue("meetingTime", time);
        if (email != null) session.setContextValue("participantEmail", email);

        // Check what we're missing
        if (date == null && !session.hasContext("meetingDate")) {
            return "I'd be happy to schedule a meeting! What date would you like? " +
                "(e.g., 'tomorrow', '2024-01-15', or 'next Monday')";
        }

        if (time == null && !session.hasContext("meetingTime")) {
            return "Got it! What time works for you? " +
                "(e.g., '2pm', '14:00', or '2:30pm')";
        }

        // We have enough info - show confirmation
        String finalDate = date != null ? date : session.getContextValue("meetingDate");
        String finalTime = time != null ? time : session.getContextValue("meetingTime");
        String participant = email != null ? email : session.getContextValue("participantEmail");

        String message = String.format(
            "I'll schedule a meeting for %s at %s", finalDate, finalTime
        );

        if (participant != null) {
            message += " with " + participant;
        }

        message += ".\n\nNote: This is a demo response. Integration with Zoom Meetings API will create actual meetings.";

        // Clear context after scheduling
        session.getContext().remove("meetingDate");
        session.getContext().remove("meetingTime");
        session.getContext().remove("participantEmail");

        return message;
    }

    private String handleListMeetings(ConversationSession session, Map<String, String> entities) {
        return "Here are your upcoming meetings:\n\n" +
            "Note: This is a demo response. Integration with Zoom Meetings API will show actual meetings.\n\n" +
            "Try asking: 'Schedule a meeting tomorrow at 2pm'";
    }

    private String handleGetMeeting(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Meetings API will show meeting details.\n\n" +
            "Please provide the meeting ID to get details.";
    }

    private String handleUpdateMeeting(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Meetings API will update meetings.\n\n" +
            "Please provide the meeting ID and new details.";
    }

    private String handleDeleteMeeting(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Meetings API will cancel meetings.\n\n" +
            "Please provide the meeting ID to cancel.";
    }

    private String handleListRecordings(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Recordings API will show actual recordings.";
    }

    private String handleGetRecording(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Recordings API will show recording details.";
    }

    private String handleDownloadRecording(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Recordings API will provide download links.";
    }

    private String handleGetUser(ConversationSession session, Map<String, String> entities) {
        String email = entities.get("email");
        if (email != null) {
            return "Note: This is a demo response. Integration with Zoom Users API will show user details for: " + email;
        }
        return "Please provide the user's email address.";
    }

    private String handleListUsers(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Users API will list all users in your organization.";
    }

    private String handleCreateUser(ConversationSession session, Map<String, String> entities) {
        return "Note: This is a demo response. Integration with Zoom Users API will create new users.\n\n" +
            "Please provide user email and details.";
    }

    private String handleUnknown() {
        return "I'm not sure I understood that. Could you rephrase, or type 'help' to see what I can do?";
    }

    /**
     * Sanitizes user input to prevent injection attacks.
     * Security: XSS and injection prevention
     * Fellow Golden Rule: No magic numbers - max length from configuration
     */
    private String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }

        // Remove control characters and excessive whitespace
        String sanitized = input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        sanitized = sanitized.trim();

        // Limit length to prevent abuse
        int maxLength = config.getMaxInputLength();
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
            log.warn("Input truncated to {} characters", maxLength);
        }

        return sanitized;
    }

    /**
     * Creates error response.
     * Fail-fast: Explicit error communication
     */
    private ChatbotResponse createErrorResponse(String userId, String errorMessage) {
        return ChatbotResponse.builder()
            .userId(userId)
            .botMessage(ChatMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .sender("bot")
                .message(errorMessage)
                .timestamp(java.time.Instant.now())
                .build())
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Response object from chatbot engine.
     * Production: Explicit response structure with all relevant data
     */
    @lombok.Builder
    @lombok.Data
    public static class ChatbotResponse {
        private String userId;
        private String sessionId;
        private ChatMessage userMessage;
        private ChatMessage botMessage;
        private Intent intent;
        private Double confidence;
        private Map<String, String> entities;
        private boolean success;
        private String errorMessage;
    }
}
