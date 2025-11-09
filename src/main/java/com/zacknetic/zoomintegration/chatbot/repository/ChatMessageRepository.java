package com.zacknetic.zoomintegration.chatbot.repository;

import com.zacknetic.zoomintegration.chatbot.entities.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for chat message persistence.
 *
 * Production: Spring Data JPA for clean data access
 * Security: SQL injection prevention via JPA
 * Explicit: Clear query methods with meaningful names
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /**
     * Find message by message ID.
     * Production: Natural key lookup
     */
    Optional<ChatMessageEntity> findByMessageId(String messageId);

    /**
     * Find all messages in a conversation, ordered chronologically.
     * Production: Conversation history retrieval
     */
    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    /**
     * Find messages by sender type (user or bot).
     * Production: Filter by message source
     */
    List<ChatMessageEntity> findByConversationIdAndSenderOrderByCreatedAtAsc(
        String conversationId,
        String sender
    );

    /**
     * Find recent messages in a conversation (for context window).
     * Production: Limit to recent history for performance
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.conversationId = :conversationId ORDER BY m.createdAt DESC LIMIT :limit")
    List<ChatMessageEntity> findRecentMessages(
        @Param("conversationId") String conversationId,
        @Param("limit") int limit
    );

    /**
     * Find unprocessed messages for async processing.
     * Fail-fast: Process all messages, don't let any slip through
     */
    List<ChatMessageEntity> findByProcessedFalseOrderByCreatedAtAsc();

    /**
     * Find messages with errors for monitoring.
     * Production: Error tracking and alerting
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.errorMessage IS NOT NULL ORDER BY m.createdAt DESC")
    List<ChatMessageEntity> findMessagesWithErrors();

    /**
     * Count messages by intent for analytics.
     * Production: Intent distribution metrics
     */
    long countByIntent(String intent);

    /**
     * Find low confidence messages for review.
     * Production: Quality monitoring
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.confidence IS NOT NULL AND m.confidence < :threshold ORDER BY m.createdAt DESC")
    List<ChatMessageEntity> findLowConfidenceMessages(@Param("threshold") double threshold);

    /**
     * Find messages by date range for analytics.
     * Production: Time-based reporting
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt ASC")
    List<ChatMessageEntity> findMessagesByDateRange(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    /**
     * Count messages in a conversation.
     * Production: Quick count for pagination
     */
    long countByConversationId(String conversationId);

    /**
     * Delete messages older than retention period.
     * Production: Data retention policy enforcement
     * Security: Ensures PII is not kept indefinitely
     */
    void deleteByCreatedAtBefore(Instant threshold);
}
