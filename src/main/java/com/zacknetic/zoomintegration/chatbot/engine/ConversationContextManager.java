package com.zacknetic.zoomintegration.chatbot.engine;

import com.zacknetic.zoomintegration.chatbot.config.ChatbotConfiguration;
import com.zacknetic.zoomintegration.chatbot.models.ConversationSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages conversation sessions and context across multiple users.
 *
 * Production: Thread-safe concurrent session management
 * Security: Session isolation per user
 * Fail-fast: Explicit session lifecycle management
 *
 * Note: In-memory storage for MVP. For production, persist to database/Redis.
 */
@Component
public class ConversationContextManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextManager.class);

    // Thread-safe session storage
    // Production: ConcurrentHashMap for multi-threaded access
    // Future: Move to Redis for distributed deployment
    private final Map<String, ConversationSession> activeSessions = new ConcurrentHashMap<>();

    private final ChatbotConfiguration config;

    public ConversationContextManager(ChatbotConfiguration config) {
        this.config = config;
    }

    /**
     * Gets or creates a conversation session.
     *
     * Production: Lazy session creation
     * Security: User ID required for audit trail
     *
     * @param userId User ID (can be anonymous ID for unauthenticated users)
     * @return Active conversation session
     */
    public ConversationSession getOrCreateSession(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        // Check for existing session
        ConversationSession existingSession = activeSessions.get(userId);

        if (existingSession != null) {
            // Check if session timed out
            // Fellow Golden Rule: No magic numbers - timeout from configuration
            long timeoutSeconds = config.getSessionTimeoutSeconds();

            if (existingSession.isTimedOut(timeoutSeconds)) {
                log.info("Session timed out for user: {} (timeout: {} seconds)", userId, timeoutSeconds);
                existingSession.setStatus(ConversationSession.ConversationStatus.TIMED_OUT);
                // Create new session
                return createNewSession(userId);
            }

            log.debug("Returning existing session for user: {}", userId);
            return existingSession;
        }

        // Create new session
        return createNewSession(userId);
    }

    /**
     * Creates a new conversation session.
     * Production: Factory method for consistent session creation
     */
    private ConversationSession createNewSession(String userId) {
        ConversationSession session = ConversationSession.create(userId);
        activeSessions.put(userId, session);
        log.info("Created new conversation session for user: {}, sessionId: {}",
            userId, session.getSessionId());
        return session;
    }

    /**
     * Gets session by session ID.
     * Production: For retrieving specific sessions (e.g., from webhook events)
     *
     * @param sessionId Session ID
     * @return Session or null if not found
     */
    public ConversationSession getSessionById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        return activeSessions.values().stream()
            .filter(session -> session.getSessionId().equals(sessionId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Updates session activity (call on every message).
     * Production: Keeps session alive and tracks message count
     */
    public void updateSessionActivity(String sessionId) {
        ConversationSession session = getSessionById(sessionId);
        if (session != null) {
            session.updateActivity();
            log.debug("Updated activity for session: {}", sessionId);
        }
    }

    /**
     * Ends a conversation session.
     * Production: Explicit session termination
     */
    public void endSession(String userId) {
        ConversationSession session = activeSessions.get(userId);
        if (session != null) {
            session.endConversation();
            log.info("Ended conversation session for user: {}, sessionId: {}",
                userId, session.getSessionId());
            // Keep in map for audit trail retrieval
            // Will be cleaned up by periodic cleanup task
        }
    }

    /**
     * Stores a context value for the session.
     * Production: Type-safe context storage
     */
    public void setContext(String userId, String key, String value) {
        ConversationSession session = getOrCreateSession(userId);
        session.setContextValue(key, value);
        log.debug("Set context for user {}: {} = {}", userId, key, value);
    }

    /**
     * Retrieves a context value from the session.
     * Production: Safe context retrieval
     */
    public String getContext(String userId, String key) {
        ConversationSession session = activeSessions.get(userId);
        if (session == null) {
            return null;
        }
        return session.getContextValue(key);
    }

    /**
     * Checks if session has a specific context key.
     */
    public boolean hasContext(String userId, String key) {
        ConversationSession session = activeSessions.get(userId);
        return session != null && session.hasContext(key);
    }

    /**
     * Clears all context for a session.
     * Production: Reset conversation state
     */
    public void clearContext(String userId) {
        ConversationSession session = activeSessions.get(userId);
        if (session != null) {
            session.getContext().clear();
            log.info("Cleared context for user: {}", userId);
        }
    }

    /**
     * Gets count of active sessions.
     * Production: For monitoring and metrics
     */
    public int getActiveSessionCount() {
        long activeCount = activeSessions.values().stream()
            .filter(session -> session.getStatus() == ConversationSession.ConversationStatus.ACTIVE)
            .count();
        return (int) activeCount;
    }

    /**
     * Cleanup timed out and ended sessions.
     * Production: Should be called periodically (e.g., via scheduled task)
     * Memory Management: Prevents memory leaks from abandoned sessions
     * Fellow Golden Rule: No magic numbers - cleanup threshold from configuration
     */
    public int cleanupInactiveSessions() {
        int removedCount = 0;

        long timeoutSeconds = config.getSessionTimeoutSeconds();
        long cleanupSeconds = config.getEndedSessionCleanupSeconds();

        for (Map.Entry<String, ConversationSession> entry : activeSessions.entrySet()) {
            ConversationSession session = entry.getValue();

            // Remove if timed out or ended for more than cleanup threshold
            if (session.isTimedOut(timeoutSeconds) ||
                (session.getStatus() != ConversationSession.ConversationStatus.ACTIVE &&
                    session.getEndedAt() != null &&
                    session.getEndedAt().plusSeconds(cleanupSeconds).isBefore(java.time.Instant.now()))) {

                activeSessions.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.info("Cleaned up {} inactive sessions (timeout: {}s, cleanup: {}s)",
                removedCount, timeoutSeconds, cleanupSeconds);
        }

        return removedCount;
    }

    /**
     * Gets all active sessions (for admin/monitoring).
     * Production: Defensive copy to prevent external modification
     */
    public Map<String, ConversationSession> getAllSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }
}
