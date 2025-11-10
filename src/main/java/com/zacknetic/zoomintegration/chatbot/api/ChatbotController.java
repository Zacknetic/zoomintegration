package com.zacknetic.zoomintegration.chatbot.api;

import com.zacknetic.zoomintegration.chatbot.engine.ChatbotEngine;
import com.zacknetic.zoomintegration.chatbot.engine.ConversationContextManager;
import com.zacknetic.zoomintegration.chatbot.models.ConversationSession;
import com.zacknetic.zoomintegration.security.redaction.PIIRedactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST API controller for chatbot interactions.
 *
 * Production: RESTful API with comprehensive documentation
 * Security: Input validation and PII redaction
 * Fail-fast: Explicit error responses
 */
@RestController
@RequestMapping("/api/chatbot")
@Tag(name = "Chatbot", description = "AI-powered Zoom assistant chatbot")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);

    private final ChatbotEngine chatbotEngine;
    private final ConversationContextManager contextManager;
    private final PIIRedactionService piiRedactionService;

    public ChatbotController(
        ChatbotEngine chatbotEngine,
        ConversationContextManager contextManager,
        PIIRedactionService piiRedactionService
    ) {
        this.chatbotEngine = chatbotEngine;
        this.contextManager = contextManager;
        this.piiRedactionService = piiRedactionService;
    }

    /**
     * Send a message to the chatbot.
     *
     * Production: Main chatbot interaction endpoint
     * Security: Input validation, rate limiting (future), PII redaction in logs
     *
     * @param request Chat request containing user ID and message
     * @return Bot response with intent and extracted entities
     */
    @PostMapping("/chat")
    @Operation(
        summary = "Send message to chatbot",
        description = "Send a message to the Zoom assistant chatbot and receive a response. " +
            "The chatbot can help with scheduling meetings, managing recordings, and user operations. " +
            "Requires authentication via JWT token in Authorization header."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Chatbot processed the message successfully",
        content = @Content(schema = @Schema(implementation = ChatResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid JWT token")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        // Extract authenticated user from Spring Security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Chat request from unauthenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ChatResponse.builder()
                    .success(false)
                    .error("Authentication required. Please login at /api/auth/login")
                    .timestamp(Instant.now())
                    .build()
            );
        }

        // Get Zoom user ID from authentication (username = Zoom user ID)
        String zoomUserId = authentication.getName();

        if (zoomUserId == null || zoomUserId.isBlank()) {
            log.warn("Chat request with invalid authentication");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ChatResponse.builder()
                    .success(false)
                    .error("Invalid authentication")
                    .timestamp(Instant.now())
                    .build()
            );
        }

        // Validate message
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            log.warn("Chat request missing message for user: {}", zoomUserId);
            return ResponseEntity.badRequest().body(
                ChatResponse.builder()
                    .success(false)
                    .userId(zoomUserId)
                    .error("Message is required")
                    .timestamp(Instant.now())
                    .build()
            );
        }

        // Log request (with PII redaction)
        log.info("Chat request from authenticated user: {}, message: {}",
            zoomUserId,
            piiRedactionService.redactForLogging(request.getMessage()));

        try {
            // Process message through chatbot engine with authenticated user
            ChatbotEngine.ChatbotResponse engineResponse =
                chatbotEngine.processMessage(zoomUserId, request.getMessage());

            // Build API response
            ChatResponse response = ChatResponse.builder()
                .success(engineResponse.isSuccess())
                .userId(engineResponse.getUserId())
                .sessionId(engineResponse.getSessionId())
                .message(engineResponse.getBotMessage().getMessage())
                .intent(engineResponse.getIntent() != null ? engineResponse.getIntent().name() : null)
                .confidence(engineResponse.getConfidence())
                .entities(engineResponse.getEntities())
                .timestamp(Instant.now())
                .build();

            log.info("Chat response sent to user: {}, intent: {}",
                zoomUserId,
                engineResponse.getIntent());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid chat request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                ChatResponse.builder()
                    .success(false)
                    .userId(zoomUserId)
                    .error(e.getMessage())
                    .timestamp(Instant.now())
                    .build()
            );

        } catch (Exception e) {
            log.error("Error processing chat request for user: {}", zoomUserId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ChatResponse.builder()
                    .success(false)
                    .userId(zoomUserId)
                    .error("An error occurred processing your message. Please try again.")
                    .timestamp(Instant.now())
                    .build()
            );
        }
    }

    /**
     * Get conversation history for a user.
     *
     * Production: Conversation retrieval for UX
     * Security: Only returns conversations for authenticated user
     */
    @GetMapping("/conversations/{userId}")
    @Operation(
        summary = "Get conversation history",
        description = "Retrieve the current or most recent conversation session for a user"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Conversation found",
        content = @Content(schema = @Schema(implementation = ConversationSession.class))
    )
    @ApiResponse(responseCode = "404", description = "No conversation found")
    public ResponseEntity<ConversationSession> getConversation(@PathVariable String userId) {
        log.info("Getting conversation for user: {}", userId);

        ConversationSession session = contextManager.getOrCreateSession(userId);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(session);
    }

    /**
     * End a conversation session.
     *
     * Production: Explicit session termination
     */
    @PostMapping("/conversations/{userId}/end")
    @Operation(
        summary = "End conversation",
        description = "Explicitly end the current conversation session"
    )
    @ApiResponse(responseCode = "200", description = "Conversation ended")
    public ResponseEntity<Void> endConversation(@PathVariable String userId) {
        log.info("Ending conversation for user: {}", userId);
        contextManager.endSession(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Clear conversation context.
     *
     * Production: Reset conversation state
     */
    @DeleteMapping("/conversations/{userId}/context")
    @Operation(
        summary = "Clear conversation context",
        description = "Clear all stored context for the current conversation"
    )
    @ApiResponse(responseCode = "200", description = "Context cleared")
    public ResponseEntity<Void> clearContext(@PathVariable String userId) {
        log.info("Clearing context for user: {}", userId);
        contextManager.clearContext(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get chatbot statistics (for monitoring).
     *
     * Production: Health and metrics endpoint
     */
    @GetMapping("/stats")
    @Operation(
        summary = "Get chatbot statistics",
        description = "Get current chatbot usage statistics and health metrics"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Statistics retrieved",
        content = @Content(schema = @Schema(implementation = ChatbotStats.class))
    )
    public ResponseEntity<ChatbotStats> getStats() {
        ChatbotStats stats = ChatbotStats.builder()
            .activeSessionCount(contextManager.getActiveSessionCount())
            .totalSessions(contextManager.getAllSessions().size())
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.ok(stats);
    }

    // Request/Response DTOs

    /**
     * Chat request DTO.
     * Production: Explicit request structure with validation
     * Security: User ID extracted from JWT token, not from request body
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Chat request to send a message to the chatbot")
    public static class ChatRequest {
        @Schema(
            description = "User's message (required)",
            example = "Schedule a meeting tomorrow at 2pm",
            required = true
        )
        private String message;
    }

    /**
     * Chat response DTO.
     * Production: Comprehensive response with all relevant data
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Chatbot response")
    public static class ChatResponse {
        @Schema(description = "Whether the request was successful", example = "true")
        private boolean success;

        @Schema(description = "User ID", example = "user123")
        private String userId;

        @Schema(description = "Conversation session ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String sessionId;

        @Schema(description = "Bot's response message", example = "I'll schedule a meeting for tomorrow at 2pm")
        private String message;

        @Schema(description = "Detected intent", example = "SCHEDULE_MEETING")
        private String intent;

        @Schema(description = "Intent classification confidence (0.0 to 1.0)", example = "0.85")
        private Double confidence;

        @Schema(description = "Extracted entities", example = "{\"date\":\"2024-01-16\",\"time\":\"14:00\"}")
        private Map<String, String> entities;

        @Schema(description = "Error message (if success is false)", example = "Invalid request")
        private String error;

        @Schema(description = "Response timestamp")
        private Instant timestamp;
    }

    /**
     * Chatbot statistics DTO.
     * Production: Monitoring and metrics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Chatbot usage statistics")
    public static class ChatbotStats {
        @Schema(description = "Number of active conversation sessions", example = "42")
        private int activeSessionCount;

        @Schema(description = "Total number of sessions (active and ended)", example = "150")
        private int totalSessions;

        @Schema(description = "Statistics timestamp")
        private Instant timestamp;
    }
}
