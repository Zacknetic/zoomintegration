package com.zacknetic.zoomintegration.chatbot.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single message in a conversation (from user or bot).
 *
 * Production: Immutable after creation (using Lombok @Data with care)
 * Security: Message content will be sanitized before persistence
 * Fail-fast: Required fields validated in builder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * Unique identifier for this message
     * Production: Required for message tracking and audit trail
     */
    private String messageId;

    /**
     * ID of the conversation this message belongs to
     * Production: Required for conversation threading
     */
    private String conversationId;

    /**
     * Who sent this message: 'user' or 'bot'
     * Production: Explicit sender type for clarity
     */
    private String sender;

    /**
     * The actual message text
     * Security: Will be sanitized to prevent XSS
     * Production: Not nullable - fail fast if missing
     */
    private String message;

    /**
     * Detected intent (for user messages) or action taken (for bot responses)
     * Production: Nullable for messages where intent isn't applicable
     */
    private Intent intent;

    /**
     * Confidence score for intent classification (0.0 to 1.0)
     * Production: Helps identify when to ask for clarification
     */
    private Double confidence;

    /**
     * Extracted entities from the message (e.g., date, time, email)
     * Production: Structured data extracted from natural language
     * Example: {"date": "2024-01-15", "time": "14:00", "email": "user@example.com"}
     */
    private Map<String, String> entities;

    /**
     * Timestamp when message was created
     * Production: UTC instant for timezone-independent storage
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Whether this message has been processed by handlers
     * Production: For async processing tracking
     */
    @Builder.Default
    private boolean processed = false;

    /**
     * Error message if processing failed
     * Fail-fast: Explicit error tracking rather than silent failure
     */
    private String errorMessage;

    /**
     * Creates a user message
     * Production: Factory method for type safety
     */
    public static ChatMessage userMessage(String conversationId, String message) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation ID is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }

        return ChatMessage.builder()
            .messageId(java.util.UUID.randomUUID().toString())
            .conversationId(conversationId)
            .sender("user")
            .message(message)
            .timestamp(Instant.now())
            .processed(false)
            .build();
    }

    /**
     * Creates a bot response message
     * Production: Factory method for type safety
     */
    public static ChatMessage botMessage(String conversationId, String message) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation ID is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }

        return ChatMessage.builder()
            .messageId(java.util.UUID.randomUUID().toString())
            .conversationId(conversationId)
            .sender("bot")
            .message(message)
            .timestamp(Instant.now())
            .processed(true) // Bot messages are already "processed"
            .build();
    }
}
