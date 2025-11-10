package com.zacknetic.zoomintegration.chatbot.engine;

import com.zacknetic.zoomintegration.auth.service.UserZoomApiService;
import com.zacknetic.zoomintegration.chatbot.config.ChatbotConfiguration;
import com.zacknetic.zoomintegration.chatbot.models.ChatMessage;
import com.zacknetic.zoomintegration.chatbot.models.ConversationSession;
import com.zacknetic.zoomintegration.chatbot.models.Intent;
import com.zacknetic.zoomintegration.zoom.api.ZoomMeetingsApiClient;
import com.zacknetic.zoomintegration.zoom.models.ZoomMeeting;
import com.zacknetic.zoomintegration.zoom.models.ZoomMeetingList;
import com.zacknetic.zoomintegration.zoom.models.ZoomMeetingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
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
    private final ZoomMeetingsApiClient meetingsApiClient;
    private final UserZoomApiService userZoomApiService;
    private final TimezoneService timezoneService;

    public ChatbotEngine(
        IntentClassifier intentClassifier,
        EntityExtractor entityExtractor,
        ConversationContextManager contextManager,
        ChatbotConfiguration config,
        ZoomMeetingsApiClient meetingsApiClient,
        UserZoomApiService userZoomApiService,
        TimezoneService timezoneService
    ) {
        this.intentClassifier = intentClassifier;
        this.entityExtractor = entityExtractor;
        this.contextManager = contextManager;
        this.config = config;
        this.meetingsApiClient = meetingsApiClient;
        this.userZoomApiService = userZoomApiService;
        this.timezoneService = timezoneService;
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
     * @param timezone User's timezone ID (optional)
     * @return ChatbotResponse containing bot's reply and metadata
     */
    public ChatbotResponse processMessage(String userId, String messageText, String timezone) {
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

        log.info("Processing message from user: {} with timezone: {}", userId, timezone);

        try {
            // Get or create conversation session
            ConversationSession session = contextManager.getOrCreateSession(userId);
            
            // Store timezone in session context if provided
            if (timezone != null && !timezone.isBlank()) {
                session.setContextValue("timezone", timezone);
            }

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
        String duration = entities.get("duration");

        // Store entities in context for multi-turn conversation
        if (date != null) session.setContextValue("meetingDate", date);
        if (time != null) session.setContextValue("meetingTime", time);
        if (email != null) session.setContextValue("participantEmail", email);
        if (duration != null) session.setContextValue("meetingDuration", duration);

        // Check what we're missing
        if (date == null && !session.hasContext("meetingDate")) {
            return "I'd be happy to schedule a meeting! What date would you like? " +
                "(e.g., 'tomorrow', '2024-01-15', or 'next Monday')";
        }

        if (time == null && !session.hasContext("meetingTime")) {
            return "Got it! What time works for you? " +
                "(e.g., '2pm', '14:00', or '2:30pm')";
        }

        // We have enough info - create the meeting via Zoom API
        String finalDate = date != null ? date : session.getContextValue("meetingDate");
        String finalTime = time != null ? time : session.getContextValue("meetingTime");
        String participant = email != null ? email : session.getContextValue("participantEmail");
        String meetingDuration = duration != null ? duration : session.getContextValue("meetingDuration");
        String userTimezone = session.getContextValue("timezone"); // Get user's timezone

        try {
            // Convert local time to UTC for Zoom API
            String startTimeUtc;
            if (userTimezone != null && !userTimezone.isBlank()) {
                startTimeUtc = timezoneService.convertLocalToUtc(finalDate, finalTime, userTimezone);
            } else {
                // Fallback to simple combination if no timezone
                startTimeUtc = combineDateTime(finalDate, finalTime);
            }

            // Parse duration or use default
            int durationMinutes = 60; // default
            if (meetingDuration != null) {
                try {
                    durationMinutes = Integer.parseInt(meetingDuration);
                } catch (NumberFormatException e) {
                    log.warn("Invalid duration format: {}", meetingDuration);
                }
            }

            // Build meeting request
            ZoomMeetingRequest request = ZoomMeetingRequest.builder()
                .topic("Scheduled Meeting") // Default topic
                .type(2) // Scheduled meeting
                .startTime(startTimeUtc)
                .duration(durationMinutes)
                .timezone("UTC") // Always store in UTC
                .settings(com.zacknetic.zoomintegration.zoom.models.ZoomMeetingSettings.builder()
                    .hostVideo(true)
                    .participantVideo(true)
                    .waitingRoom(true)
                    .joinBeforeHost(false)
                    .muteUponEntry(false)
                    .build())
                .build();

            // Create meeting via Zoom API using user's token
            log.info("Creating Zoom meeting for user: {} at UTC: {} (local: {} {} {})", 
                session.getUserId(), startTimeUtc, finalDate, finalTime, userTimezone);

            // Get user's Zoom access token
            String userAccessToken = userZoomApiService.getAccessTokenForUser(session.getUserId());

            // Create meeting with user's token (userId="me" uses the authenticated user)
            ZoomMeeting meeting = meetingsApiClient.createMeetingWithUserToken("me", request, userAccessToken);

            // Clear context after successful scheduling
            session.getContext().remove("meetingDate");
            session.getContext().remove("meetingTime");
            session.getContext().remove("participantEmail");
            session.getContext().remove("meetingDuration");

            // Format success response
            StringBuilder response = new StringBuilder();
            response.append("Meeting scheduled successfully!\n\n");
            
            // Display time in user's local timezone
            if (userTimezone != null && !userTimezone.isBlank()) {
                String formattedTime = timezoneService.formatLocalDateTime(finalDate, finalTime, userTimezone);
                response.append("When: ").append(formattedTime).append("\n");
            } else {
                response.append("When: ").append(formatDateTime(finalDate, finalTime)).append("\n");
            }
            
            response.append("Duration: ").append(durationMinutes).append(" minutes\n");
            response.append("Meeting ID: ").append(meeting.getId()).append("\n");

            if (meeting.getPassword() != null) {
                response.append("Password: ").append(meeting.getPassword()).append("\n");
            }

            response.append("\nJoin URL: ").append(meeting.getJoinUrl()).append("\n");

            if (participant != null) {
                // Log the invite attempt (email sending not yet implemented)
                sendMeetingInvite(participant, meeting, finalDate, finalTime, durationMinutes, userTimezone);
                
                response.append("\n📧 To invite ").append(participant).append(":\n");
                response.append("   Please share the join URL above with them.\n");
                response.append("   (Automatic email invites require email service integration)");
            }

            return response.toString();

        } catch (IOException e) {
            log.error("Failed to create Zoom meeting", e);
            // Clear context so user can try again
            session.getContext().remove("meetingDate");
            session.getContext().remove("meetingTime");
            session.getContext().remove("participantEmail");
            session.getContext().remove("meetingDuration");

            // Provide specific error message for authentication failures
            if (isAuthenticationError(e)) {
                return "Zoom API authentication failed. Please check your Zoom API credentials.\n\n" +
                    "The application may be using invalid or mock credentials. " +
                    "Check the application logs for configuration details.";
            }

            return "Sorry, I encountered an error creating the meeting. Please try again later.";
        } catch (IllegalArgumentException e) {
            log.warn("Invalid meeting parameters", e);
            return "I couldn't create the meeting: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error creating meeting", e);
            return "An unexpected error occurred. Please try again.";
        }
    }

    private String handleListMeetings(ConversationSession session, Map<String, String> entities) {
        try {
            // Fetch upcoming meetings from Zoom API using user's token
            log.info("Fetching upcoming meetings for user: {}", session.getUserId());

            // Get user's Zoom access token
            String userAccessToken = userZoomApiService.getAccessTokenForUser(session.getUserId());

            ZoomMeetingList meetingList = meetingsApiClient.listMeetingsWithUserToken(
                "me", "scheduled", 10, 1, userAccessToken
            );

            if (meetingList.isEmpty()) {
                return "You have no upcoming meetings.\n\n" +
                    "Would you like to schedule one? Just say 'schedule a meeting tomorrow at 2pm'";
            }

            // Format meetings list
            StringBuilder response = new StringBuilder();
            response.append("Your upcoming meetings:\n\n");

            List<ZoomMeeting> meetings = meetingList.getMeetings();
            String userTimezone = session.getContextValue("timezone");
            
            for (int i = 0; i < meetings.size(); i++) {
                ZoomMeeting meeting = meetings.get(i);

                // Store meeting ID in context for follow-up actions
                session.setContextValue("meeting_" + (i + 1), meeting.getId().toString());

                response.append(i + 1).append(". ");
                response.append(meeting.getTopic() != null ? meeting.getTopic() : "Meeting");
                response.append("\n");

                if (meeting.getStartTime() != null) {
                    // Convert UTC time to user's local timezone for display
                    if (userTimezone != null && !userTimezone.isBlank()) {
                        try {
                            String localTime = timezoneService.convertUtcToLocal(meeting.getStartTime(), userTimezone);
                            response.append("   ").append(localTime).append("\n");
                        } catch (Exception e) {
                            log.warn("Failed to convert meeting time to local timezone", e);
                            response.append("   ").append(meeting.getStartTime()).append("\n");
                        }
                    } else {
                        response.append("   ").append(meeting.getStartTime()).append("\n");
                    }
                }

                response.append("   ID: ").append(meeting.getId()).append("\n\n");
            }

            response.append("Tip: Say 'get meeting 1' for details or 'delete meeting 2' to cancel.");

            return response.toString();

        } catch (IOException e) {
            log.error("Failed to list meetings", e);
            if (isAuthenticationError(e)) {
                return "Zoom API authentication failed. Please check your Zoom API credentials.\n\n" +
                    "The application may be using invalid or mock credentials. " +
                    "Check the application logs for configuration details.";
            }
            return "Sorry, I couldn't fetch your meetings right now. Please try again later.";
        } catch (Exception e) {
            log.error("Unexpected error listing meetings", e);
            return "An unexpected error occurred while fetching meetings.";
        }
    }

    private String handleGetMeeting(ConversationSession session, Map<String, String> entities) {
        // Try to get meeting ID from context (e.g., "get meeting 1" after listing)
        String meetingId = null;

        // Check if user referenced a numbered meeting from the list
        for (String key : session.getContext().keySet()) {
            if (key.startsWith("meeting_")) {
                // This is a simplified approach - in production, parse the number from the message
                meetingId = session.getContextValue(key);
                break;
            }
        }

        if (meetingId == null) {
            return "Please list your meetings first, then say 'get meeting 1' to see details.\n\n" +
                "Or provide a specific meeting ID.";
        }

        try {
            log.info("Fetching meeting details for ID: {}", meetingId);
            
            // Get user's Zoom access token
            String userAccessToken = userZoomApiService.getAccessTokenForUser(session.getUserId());
            
            ZoomMeeting meeting = meetingsApiClient.getMeetingWithUserToken(meetingId, userAccessToken);

            // Format meeting details
            StringBuilder response = new StringBuilder();
            response.append("Meeting Details:\n\n");
            response.append("Topic: ").append(meeting.getTopic() != null ? meeting.getTopic() : "N/A").append("\n");

            if (meeting.getStartTime() != null) {
                String userTimezone = session.getContextValue("timezone");
                // Convert UTC time to user's local timezone for display
                if (userTimezone != null && !userTimezone.isBlank()) {
                    try {
                        String localTime = timezoneService.convertUtcToLocal(meeting.getStartTime(), userTimezone);
                        response.append("When: ").append(localTime).append("\n");
                    } catch (Exception e) {
                        log.warn("Failed to convert meeting time to local timezone", e);
                        response.append("When: ").append(meeting.getStartTime()).append("\n");
                    }
                } else {
                    response.append("When: ").append(meeting.getStartTime()).append("\n");
                }
            }

            if (meeting.getDuration() != null) {
                response.append("Duration: ").append(meeting.getDuration()).append(" minutes\n");
            }

            response.append("Meeting ID: ").append(meeting.getId()).append("\n");

            if (meeting.getPassword() != null) {
                response.append("Password: ").append(meeting.getPassword()).append("\n");
            }

            if (meeting.getJoinUrl() != null) {
                response.append("\nJoin URL: ").append(meeting.getJoinUrl()).append("\n");
            }

            // Settings
            if (meeting.getSettings() != null) {
                response.append("\nSettings:\n");
                response.append("   • Waiting room: ")
                    .append(meeting.getSettings().getWaitingRoom() ? "Enabled" : "Disabled").append("\n");
                response.append("   • Host video: ")
                    .append(meeting.getSettings().getHostVideo() ? "On" : "Off").append("\n");
                response.append("   • Participant video: ")
                    .append(meeting.getSettings().getParticipantVideo() ? "On" : "Off").append("\n");
            }

            return response.toString();

        } catch (IOException e) {
            log.error("Failed to get meeting details", e);
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return "Meeting not found. It may have been deleted or the ID is incorrect.";
            }
            return "Sorry, I couldn't fetch the meeting details. Please try again later.";
        } catch (Exception e) {
            log.error("Unexpected error getting meeting", e);
            return "An unexpected error occurred while fetching meeting details.";
        }
    }

    private String handleUpdateMeeting(ConversationSession session, Map<String, String> entities) {
        // Get meeting ID from context
        String meetingId = getMeetingIdFromContext(session);
        if (meetingId == null) {
            return "Please list your meetings first, then say 'update meeting 1' to modify it.\n\n" +
                "Or provide a specific meeting ID.";
        }

        // Check what updates the user wants to make
        String date = entities.get("date");
        String time = entities.get("time");
        String duration = entities.get("duration");

        // Store updates in context for multi-turn conversation
        if (date != null) session.setContextValue("updateDate", date);
        if (time != null) session.setContextValue("updateTime", time);
        if (duration != null) session.setContextValue("updateDuration", duration);

        // Check if we have any updates to apply
        boolean hasDateUpdate = date != null || session.hasContext("updateDate");
        boolean hasTimeUpdate = time != null || session.hasContext("updateTime");
        boolean hasDurationUpdate = duration != null || session.hasContext("updateDuration");

        if (!hasDateUpdate && !hasTimeUpdate && !hasDurationUpdate) {
            return "What would you like to update?\n\n" +
                "You can change:\n" +
                "- Date (e.g., 'reschedule to tomorrow')\n" +
                "- Time (e.g., 'change to 3pm')\n" +
                "- Duration (e.g., 'make it 90 minutes')";
        }

        try {
            // Build update request with only the fields to change
            ZoomMeetingRequest.ZoomMeetingRequestBuilder builder = ZoomMeetingRequest.builder();

            // Update start time if date or time changed
            if (hasDateUpdate || hasTimeUpdate) {
                String finalDate = date != null ? date : session.getContextValue("updateDate");
                String finalTime = time != null ? time : session.getContextValue("updateTime");

                if (finalDate != null && finalTime != null) {
                    String startTime = combineDateTime(finalDate, finalTime);
                    builder.startTime(startTime);
                }
            }

            // Update duration if specified
            if (hasDurationUpdate) {
                String durationStr = duration != null ? duration : session.getContextValue("updateDuration");
                try {
                    int durationMinutes = Integer.parseInt(durationStr);
                    builder.duration(durationMinutes);
                } catch (NumberFormatException e) {
                    log.warn("Invalid duration format: {}", durationStr);
                    return "Invalid duration format. Please specify duration in minutes (e.g., '60' or '90').";
                }
            }

            ZoomMeetingRequest request = builder.build();

            // Update meeting via Zoom API
            log.info("Updating meeting ID: {} for user: {}", meetingId, session.getUserId());
            meetingsApiClient.updateMeeting(meetingId, request);

            // Clear update context
            session.getContext().remove("updateDate");
            session.getContext().remove("updateTime");
            session.getContext().remove("updateDuration");

            return "Meeting updated successfully!\n\n" +
                "Your changes have been saved. All participants will be notified.";

        } catch (IOException e) {
            log.error("Failed to update meeting", e);
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return "Meeting not found. It may have been deleted.";
            }
            return "Sorry, I couldn't update the meeting. Please try again later.";
        } catch (IllegalArgumentException e) {
            log.warn("Invalid update parameters", e);
            return "Invalid update parameters: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error updating meeting", e);
            return "An unexpected error occurred while updating the meeting.";
        }
    }

    private String handleDeleteMeeting(ConversationSession session, Map<String, String> entities) {
        // Get meeting ID from context (supports "delete meeting 2")
        String meetingId = getMeetingIdFromContext(session);
        if (meetingId == null) {
            return "Please list your meetings first, then say 'delete meeting 1' to cancel it.\n\n" +
                "Or provide a specific meeting ID.";
        }

        try {
            // Delete meeting via Zoom API
            // scheduleForReminder=true sends cancellation email to participants
            log.info("Deleting meeting ID: {} for user: {}", meetingId, session.getUserId());
            meetingsApiClient.deleteMeeting(meetingId, null, true);

            // Clear meeting context after successful deletion
            clearMeetingContext(session);

            return "Meeting cancelled successfully!\n\n" +
                "Cancellation emails have been sent to all participants.";

        } catch (IOException e) {
            log.error("Failed to delete meeting", e);
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                // Meeting might already be deleted - clear context anyway
                clearMeetingContext(session);
                return "Meeting not found. It may have already been cancelled.";
            }
            return "Sorry, I couldn't cancel the meeting. Please try again later.";
        } catch (Exception e) {
            log.error("Unexpected error deleting meeting", e);
            return "An unexpected error occurred while cancelling the meeting.";
        }
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
     * Combines date and time strings into ISO 8601 format for Zoom API.
     * Example: "2024-01-15" + "14:00" → "2024-01-15T14:00:00"
     *
     * Production: Handles various date/time formats
     * Fail-fast: Throws IllegalArgumentException for invalid input
     */
    private String combineDateTime(String date, String time) {
        if (date == null || time == null) {
            throw new IllegalArgumentException("Date and time are required");
        }

        // Ensure time has seconds format (HH:mm:ss)
        String fullTime = time;
        if (time.contains(":") && time.split(":").length == 2) {
            fullTime = time + ":00";
        } else if (!time.contains(":")) {
            // Handle cases like "14" or "2pm"
            fullTime = time + ":00:00";
        }

        // Combine into ISO 8601 format
        return date + "T" + fullTime;
    }

    /**
     * Formats date/time for user-friendly display.
     * Example: "2024-01-15" + "14:00" → "January 15, 2024 at 2:00 PM"
     *
     * Production: Fallback to simple format if parsing fails
     */
    private String formatDateTime(String date, String time) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            LocalTime localTime = LocalTime.parse(time);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");
            return localDate.atTime(localTime).format(formatter);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date/time for formatting: {} {}", date, time);
            return date + " at " + time;
        }
    }

    /**
     * Gets meeting ID from session context based on numbered reference.
     * Supports: "delete meeting 2" → retrieves meeting_2 from context
     *
     * Production: Simple implementation - returns first meeting found
     * TODO: Parse meeting number from user message for specific selection
     */
    private String getMeetingIdFromContext(ConversationSession session) {
        // For now, return the first meeting in context
        // In production, we'd parse the meeting number from the user's message
        for (String key : session.getContext().keySet()) {
            if (key.startsWith("meeting_")) {
                return session.getContextValue(key);
            }
        }
        return null;
    }

    /**
     * Clears meeting-related context after operations complete.
     * Production: Clean up session state to prevent stale data
     */
    private void clearMeetingContext(ConversationSession session) {
        session.getContext().keySet().removeIf(key -> key.startsWith("meeting_"));
    }

    /**
     * Checks if an IOException is due to authentication failure.
     * Production: Provides specific error messages for auth issues
     */
    private boolean isAuthenticationError(IOException e) {
        if (e == null) {
            return false;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("invalid_client") ||
               lowerMessage.contains("authentication") ||
               lowerMessage.contains("unauthorized") ||
               lowerMessage.contains("access token") ||
               lowerMessage.contains("401");
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
     * Logs meeting invite details for the participant.
     * 
     * TODO: To implement actual email sending, integrate with an email service:
     * 
     * Option 1 - Spring Boot Mail (SMTP):
     *   1. Add dependency: spring-boot-starter-mail
     *   2. Configure SMTP in application.properties (Gmail, SendGrid, etc.)
     *   3. Use JavaMailSender to send emails with calendar attachments
     * 
     * Option 2 - SendGrid API:
     *   1. Add dependency: sendgrid-java
     *   2. Get SendGrid API key
     *   3. Use SendGrid client to send transactional emails
     * 
     * Option 3 - AWS SES:
     *   1. Add dependency: aws-java-sdk-ses
     *   2. Configure AWS credentials
     *   3. Use SES client to send emails
     * 
     * @param participantEmail Email address to send invite to
     * @param meeting Created Zoom meeting
     * @param date Meeting date (local)
     * @param time Meeting time (local)
     * @param duration Meeting duration in minutes
     * @param timezone User's timezone
     */
    private void sendMeetingInvite(String participantEmail, ZoomMeeting meeting, 
                                   String date, String time, int duration, String timezone) {
        // Format the meeting time for display
        String formattedTime;
        if (timezone != null && !timezone.isBlank()) {
            formattedTime = timezoneService.formatLocalDateTime(date, time, timezone);
        } else {
            formattedTime = formatDateTime(date, time);
        }
        
        log.info("=== Meeting Invite Details ===");
        log.info("Recipient: {}", participantEmail);
        log.info("Time: {}", formattedTime);
        log.info("Duration: {} minutes", duration);
        log.info("Join URL: {}", meeting.getJoinUrl());
        log.info("Meeting ID: {}", meeting.getId());
        if (meeting.getPassword() != null) {
            log.info("Password: {}", meeting.getPassword());
        }
        log.info("==============================");
        
        // Example email implementation (commented out):
        /*
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setTo(participantEmail);
            helper.setSubject("Zoom Meeting Invitation");
            helper.setText(String.format(
                "You're invited to a Zoom meeting!\n\n" +
                "Time: %s\n" +
                "Duration: %d minutes\n\n" +
                "Join URL: %s\n" +
                "Meeting ID: %s\n" +
                "Password: %s",
                formattedTime, duration, meeting.getJoinUrl(), 
                meeting.getId(), meeting.getPassword()
            ));
            
            mailSender.send(message);
            log.info("Email invite sent successfully to {}", participantEmail);
        } catch (Exception e) {
            log.error("Failed to send email invite", e);
            throw new RuntimeException("Failed to send invite email", e);
        }
        */
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
