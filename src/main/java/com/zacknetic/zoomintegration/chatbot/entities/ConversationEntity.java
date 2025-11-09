package com.zacknetic.zoomintegration.chatbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for persisting conversation sessions.
 *
 * Production: Audit trail for all conversations
 * Security: User ID tracked for authorization
 * Fail-fast: NOT NULL constraints on required fields
 */
@Entity
@Table(name = "conversations", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique session identifier (UUID).
     * Production: Indexed for fast lookup
     */
    @Column(name = "session_id", nullable = false, unique = true, length = 255)
    private String sessionId;

    /**
     * User ID (can be Zoom user ID or anonymous session ID).
     * Security: Required for audit trail
     * Production: Indexed for user-specific queries
     */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    /**
     * When conversation started.
     * Production: UTC timestamps for timezone independence
     */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * When conversation ended (null if still active).
     */
    @Column(name = "ended_at")
    private Instant endedAt;

    /**
     * Total message count in this conversation.
     * Production: For analytics and rate limiting
     */
    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private Integer messageCount = 0;

    /**
     * Conversation status.
     * Production: Indexed for filtering active conversations
     */
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Last activity timestamp (updated on every message).
     * Production: For session timeout detection
     */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    /**
     * Audit fields.
     * Production: Track record creation and updates
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Update timestamp on every modification.
     * Production: Automatic audit trail
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Set defaults on insert.
     * Production: Fail-fast with NOT NULL constraints
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        if (this.lastActivityAt == null) {
            this.lastActivityAt = now;
        }
        if (this.messageCount == null) {
            this.messageCount = 0;
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }
}
