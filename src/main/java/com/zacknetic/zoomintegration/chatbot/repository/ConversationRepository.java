package com.zacknetic.zoomintegration.chatbot.repository;

import com.zacknetic.zoomintegration.chatbot.entities.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for conversation persistence.
 *
 * Production: Spring Data JPA for clean data access
 * Security: SQL injection prevention via JPA
 * Explicit: Clear query methods with meaningful names
 */
@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {

    /**
     * Find conversation by session ID.
     * Production: Natural key lookup
     */
    Optional<ConversationEntity> findBySessionId(String sessionId);

    /**
     * Find all conversations for a user.
     * Production: User-specific queries for audit trail
     */
    List<ConversationEntity> findByUserIdOrderByStartedAtDesc(String userId);

    /**
     * Find active conversations for a user.
     * Production: Filter by status
     */
    List<ConversationEntity> findByUserIdAndStatus(String userId, String status);

    /**
     * Find the most recent active conversation for a user.
     * Production: Session resumption capability
     */
    @Query("SELECT c FROM ConversationEntity c WHERE c.userId = :userId AND c.status = 'ACTIVE' ORDER BY c.lastActivityAt DESC")
    Optional<ConversationEntity> findMostRecentActiveConversation(@Param("userId") String userId);

    /**
     * Find conversations that have timed out (no activity for > 30 minutes).
     * Production: For cleanup jobs
     */
    @Query("SELECT c FROM ConversationEntity c WHERE c.status = 'ACTIVE' AND c.lastActivityAt < :timeoutThreshold")
    List<ConversationEntity> findTimedOutConversations(@Param("timeoutThreshold") Instant timeoutThreshold);

    /**
     * Find ended conversations older than retention period.
     * Production: For data retention policy enforcement
     */
    @Query("SELECT c FROM ConversationEntity c WHERE c.status != 'ACTIVE' AND c.endedAt < :retentionThreshold")
    List<ConversationEntity> findOldEndedConversations(@Param("retentionThreshold") Instant retentionThreshold);

    /**
     * Count active conversations.
     * Production: For monitoring and metrics
     */
    long countByStatus(String status);

    /**
     * Get total message count for analytics.
     * Production: Aggregate metrics
     */
    @Query("SELECT SUM(c.messageCount) FROM ConversationEntity c")
    Long getTotalMessageCount();

    /**
     * Check if user has an active conversation.
     * Production: Quick existence check
     */
    boolean existsByUserIdAndStatus(String userId, String status);
}
