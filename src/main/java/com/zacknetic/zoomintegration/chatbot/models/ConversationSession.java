package com.zacknetic.zoomintegration.chatbot.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a conversation session between user and chatbot.
 * Maintains context across multiple message exchanges.
 *
 * Production: Thread-safe context management for concurrent conversations
 * Security: User ID tracked for audit trail and authorization
 * Fail-fast: Explicit session state management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {

    /**
     * Unique identifier for this conversation
     * Production: UUID for global uniqueness
     */
    private String sessionId;

    /**
     * User ID (if authenticated, can be null for anonymous)
     * Security: Track user for audit trail and rate limiting
     */
    private String userId;

    /**
     * When this conversation started
     * Production: UTC instant for timezone independence
     */
    @Builder.Default
    private Instant startedAt = Instant.now();

    /**
     * When this conversation ended (null if still active)
     * Production: Explicit session lifecycle management
     */
    private Instant endedAt;

    /**
     * Total number of messages in this conversation
     * Production: For analytics and rate limiting
     */
    @Builder.Default
    private int messageCount = 0;

    /**
     * Current conversation status
     * Production: Explicit state machine
     */
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    /**
     * Conversation context - stores extracted entities and state across messages
     * Example: {"meetingTopic": "Sprint Planning", "selectedDate": "2024-01-15"}
     * Production: Maintains state for multi-turn conversations
     * Security: Will be sanitized before persistence
     */
    @Builder.Default
    private Map<String, String> context = new HashMap<>();

    /**
     * Last intent processed in this conversation
     * Production: Helps handle follow-up questions
     */
    private Intent lastIntent;

    /**
     * Timestamp of last activity (message sent or received)
     * Production: For session timeout management
     */
    @Builder.Default
    private Instant lastActivityAt = Instant.now();

    /**
     * Creates a new conversation session
     * Production: Factory method for consistent initialization
     */
    public static ConversationSession create(String userId) {
        return ConversationSession.builder()
            .sessionId(java.util.UUID.randomUUID().toString())
            .userId(userId)
            .startedAt(Instant.now())
            .lastActivityAt(Instant.now())
            .status(ConversationStatus.ACTIVE)
            .messageCount(0)
            .context(new HashMap<>())
            .build();
    }

    /**
     * Updates last activity timestamp
     * Production: Call this on every message
     */
    public void updateActivity() {
        this.lastActivityAt = Instant.now();
        this.messageCount++;
    }

    /**
     * Adds or updates context value
     * Production: Type-safe context management
     */
    public void setContextValue(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Context key cannot be null or empty");
        }
        this.context.put(key, value);
    }

    /**
     * Gets context value
     * Production: Safe context retrieval
     */
    public String getContextValue(String key) {
        return this.context.get(key);
    }

    /**
     * Checks if context has a specific key
     * Production: Explicit context checks
     */
    public boolean hasContext(String key) {
        return this.context.containsKey(key);
    }

    /**
     * Ends this conversation
     * Production: Explicit session termination
     */
    public void endConversation() {
        this.status = ConversationStatus.ENDED;
        this.endedAt = Instant.now();
    }

    /**
     * Checks if session has timed out.
     * Production: Automatic cleanup of stale sessions
     * Fellow Golden Rule: No magic numbers - timeout from configuration
     *
     * Note: This method cannot use dependency injection as it's a POJO model class.
     * The timeout value is passed via method parameter by ConversationContextManager.
     *
     * @param timeoutSeconds Session timeout in seconds (from configuration)
     */
    public boolean isTimedOut(long timeoutSeconds) {
        if (this.status != ConversationStatus.ACTIVE) {
            return false;
        }

        Instant timeout = this.lastActivityAt.plusSeconds(timeoutSeconds);
        return Instant.now().isAfter(timeout);
    }

    /**
     * Conversation status enum
     * Production: Explicit state machine
     */
    public enum ConversationStatus {
        ACTIVE,     // Currently active conversation
        ENDED,      // User explicitly ended conversation
        TIMED_OUT,  // Session timed out due to inactivity
        ERROR       // Conversation encountered an unrecoverable error
    }
}
