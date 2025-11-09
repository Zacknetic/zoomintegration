package com.zacknetic.zoomintegration.chatbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for persisting chat messages.
 *
 * Production: Complete audit trail of all messages
 * Security: Messages sanitized before persistence
 * Fail-fast: NOT NULL constraints on required fields
 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_conversation_id", columnList = "conversation_id"),
    @Index(name = "idx_sender", columnList = "sender"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_intent", columnList = "intent")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique message identifier (UUID).
     */
    @Column(name = "message_id", nullable = false, unique = true, length = 255)
    private String messageId;

    /**
     * Conversation session ID this message belongs to.
     * Production: Foreign key to conversations table
     */
    @Column(name = "conversation_id", nullable = false, length = 255)
    private String conversationId;

    /**
     * Who sent this message: 'user' or 'bot'.
     * Production: Indexed for filtering by sender
     */
    @Column(name = "sender", nullable = false, length = 50)
    private String sender;

    /**
     * Message content.
     * Security: XSS-safe, sanitized before storage
     * Production: TEXT type for longer messages
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Detected intent (for user messages).
     * Production: Indexed for analytics
     */
    @Column(name = "intent", length = 100)
    private String intent;

    /**
     * Confidence score for intent classification (0.0 to 1.0).
     * Production: For quality monitoring
     */
    @Column(name = "confidence")
    private Double confidence;

    /**
     * Extracted entities in JSON format.
     * Production: JSONB in PostgreSQL for structured querying
     * Example: {"date": "2024-01-15", "time": "14:00"}
     */
    @Column(name = "entities", columnDefinition = "TEXT")
    private String entities; // JSON string

    /**
     * Whether this message has been processed.
     * Production: For async processing tracking
     */
    @Column(name = "processed", nullable = false)
    @Builder.Default
    private Boolean processed = false;

    /**
     * Error message if processing failed.
     * Fail-fast: Explicit error tracking
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Audit fields.
     * Production: Complete audit trail
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Set defaults on insert.
     * Production: Fail-fast with NOT NULL constraints
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.processed == null) {
            this.processed = false;
        }
    }
}
