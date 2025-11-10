package com.zacknetic.zoomintegration.zoom.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zacknetic.zoomintegration.config.ZoomConfig;
import com.zacknetic.zoomintegration.security.redaction.PIIRedactionService;
import com.zacknetic.zoomintegration.zoom.auth.ZoomOAuthService;
import com.zacknetic.zoomintegration.zoom.models.ZoomMeeting;
import com.zacknetic.zoomintegration.zoom.models.ZoomMeetingList;
import com.zacknetic.zoomintegration.zoom.models.ZoomMeetingRequest;
import com.zacknetic.zoomintegration.zoom.retry.RetryUtil;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Zoom Meetings API client.
 *
 * Fellow Standards:
 * - Production Mindset: All operations with retry logic, error handling
 * - Security First: OAuth tokens, PII redaction in logs
 * - Fail Fast: Explicit exceptions with context
 * - No Hacking: Production-ready implementation, no shortcuts
 *
 * Zoom API Reference: https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#tag/Meetings
 */
@Service
public class ZoomMeetingsApiClient {

    private static final Logger log = LoggerFactory.getLogger(ZoomMeetingsApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ZoomConfig zoomConfig;
    private final ZoomOAuthService oauthService;
    private final PIIRedactionService redactionService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ZoomMeetingsApiClient(
        ZoomConfig zoomConfig,
        ZoomOAuthService oauthService,
        PIIRedactionService redactionService,
        OkHttpClient httpClient,
        ObjectMapper objectMapper
    ) {
        this.zoomConfig = zoomConfig;
        this.oauthService = oauthService;
        this.redactionService = redactionService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new Zoom meeting for a user.
     *
     * Production: With retry logic for transient failures
     * Security: OAuth authentication, no credentials in logs
     *
     * @param userId User ID or email (use "me" for authenticated user)
     * @param request Meeting configuration
     * @return Created meeting details
     * @throws IOException if API call fails
     */
    public ZoomMeeting createMeeting(String userId, ZoomMeetingRequest request) throws IOException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request == null || request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("Meeting topic is required");
        }

        log.info("Creating meeting for user: {} with topic: {}",
            redactionService.redactEmail(userId), request.getTopic());

        return RetryUtil.withRetry(
            () -> {
                try {
                    return executeCreateMeeting(userId, request);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "createMeeting for user: " + userId
        );
    }

    private ZoomMeeting executeCreateMeeting(String userId, ZoomMeetingRequest request) throws IOException {
        String accessToken = oauthService.getAccessToken();
        String url = zoomConfig.getApi().getBaseUrl() + "/users/" + userId + "/meetings";

        String requestBody = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody, JSON))
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to create meeting. Status: {}, Body: {}",
                    response.code(), redacted);
                throw new IOException("Failed to create meeting: HTTP " + response.code());
            }

            ZoomMeeting meeting = objectMapper.readValue(responseBody, ZoomMeeting.class);
            log.info("Meeting created successfully. ID: {}, Join URL: [REDACTED]", meeting.getId());

            return meeting;
        }
    }

    /**
     * Create a new Zoom meeting for a user using user-specific OAuth token.
     * Uses "me" as the user ID since the token is user-specific.
     *
     * Production: With retry logic for transient failures
     * Security: Uses user's OAuth token with proper scopes
     *
     * @param userId User ID from Zoom OAuth (typically "me" for current user)
     * @param request Meeting configuration
     * @param userAccessToken User's OAuth access token with meeting:write scope
     * @return Created meeting details
     * @throws IOException if API call fails
     */
    public ZoomMeeting createMeetingWithUserToken(String userId, ZoomMeetingRequest request, String userAccessToken) throws IOException {
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalArgumentException("User access token is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request == null || request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("Meeting topic is required");
        }

        log.info("Creating meeting for user: {} with topic: {}",
            redactionService.redactEmail(userId), request.getTopic());

        return RetryUtil.withRetry(
            () -> {
                try {
                    return executeCreateMeetingWithToken(userId, request, userAccessToken);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "createMeeting for user: " + userId
        );
    }

    private ZoomMeeting executeCreateMeetingWithToken(String userId, ZoomMeetingRequest request, String userAccessToken) throws IOException {
        String url = zoomConfig.getApi().getBaseUrl() + "/users/" + userId + "/meetings";

        String requestBody = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + userAccessToken)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody, JSON))
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to create meeting. Status: {}, Body: {}",
                    response.code(), redacted);
                throw new IOException("Failed to create meeting: HTTP " + response.code());
            }

            ZoomMeeting meeting = objectMapper.readValue(responseBody, ZoomMeeting.class);
            log.info("Meeting created successfully. ID: {}, Join URL: [REDACTED]", meeting.getId());

            return meeting;
        }
    }

    /**
     * List meetings for a user using server-to-server OAuth.
     *
     * Production: Supports pagination for large result sets
     * Security: PII redaction for email addresses in logs
     *
     * @param userId User ID or email (use "me" for authenticated user)
     * @param type Meeting type filter: "scheduled", "live", "upcoming" (default: "scheduled")
     * @param pageSize Number of records per page (default: 30, max: 300)
     * @param pageNumber Page number (default: 1)
     * @return List of meetings with pagination info
     * @throws IOException if API call fails
     */
    public ZoomMeetingList listMeetings(
        String userId,
        String type,
        Integer pageSize,
        Integer pageNumber
    ) throws IOException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        String meetingType = type != null ? type : "scheduled";
        int size = pageSize != null ? Math.min(pageSize, 300) : 30;
        int page = pageNumber != null ? Math.max(pageNumber, 1) : 1;

        log.info("Listing {} meetings for user: {} (page: {}, size: {})",
            meetingType, redactionService.redactEmail(userId), page, size);

        return RetryUtil.withRetry(
            () -> {
                try {
                    return executeListMeetings(userId, meetingType, size, page);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "listMeetings for user: " + userId
        );
    }

    /**
     * List meetings for a user using user-specific OAuth token.
     * Uses "me" as the user ID since the token is user-specific.
     *
     * Production: Supports pagination for large result sets
     * Security: Uses user's OAuth token with proper scopes
     *
     * @param userId User ID from Zoom OAuth (typically from token, use "me" for current user)
     * @param type Meeting type filter: "scheduled", "live", "upcoming" (default: "scheduled")
     * @param pageSize Number of records per page (default: 30, max: 300)
     * @param pageNumber Page number (default: 1)
     * @param userAccessToken User's OAuth access token with meeting:read scope
     * @return List of meetings with pagination info
     * @throws IOException if API call fails
     */
    public ZoomMeetingList listMeetingsWithUserToken(
        String userId,
        String type,
        Integer pageSize,
        Integer pageNumber,
        String userAccessToken
    ) throws IOException {
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalArgumentException("User access token is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        String meetingType = type != null ? type : "scheduled";
        int size = pageSize != null ? Math.min(pageSize, 300) : 30;
        int page = pageNumber != null ? Math.max(pageNumber, 1) : 1;

        log.info("Listing {} meetings for user: {} (page: {}, size: {})",
            meetingType, redactionService.redactEmail(userId), page, size);

        return RetryUtil.withRetry(
            () -> {
                try {
                    return executeListMeetingsWithToken(userId, meetingType, size, page, userAccessToken);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "listMeetings for user: " + userId
        );
    }

    private ZoomMeetingList executeListMeetings(
        String userId,
        String type,
        int pageSize,
        int pageNumber
    ) throws IOException {
        String accessToken = oauthService.getAccessToken();

        HttpUrl url = HttpUrl.parse(zoomConfig.getApi().getBaseUrl() + "/users/" + userId + "/meetings")
            .newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("page_size", String.valueOf(pageSize))
            .addQueryParameter("page_number", String.valueOf(pageNumber))
            .build();

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to list meetings. Status: {}, Body: {}",
                    response.code(), redacted);
                throw new IOException("Failed to list meetings: HTTP " + response.code());
            }

            ZoomMeetingList meetingList = objectMapper.readValue(responseBody, ZoomMeetingList.class);
            log.info("Found {} meetings (page {}/{})",
                meetingList.getCount(), meetingList.getPageNumber(), meetingList.getPageCount());

            return meetingList;
        }
    }

    private ZoomMeetingList executeListMeetingsWithToken(
        String userId,
        String type,
        int pageSize,
        int pageNumber,
        String userAccessToken
    ) throws IOException {
        HttpUrl url = HttpUrl.parse(zoomConfig.getApi().getBaseUrl() + "/users/" + userId + "/meetings")
            .newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("page_size", String.valueOf(pageSize))
            .addQueryParameter("page_number", String.valueOf(pageNumber))
            .build();

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + userAccessToken)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to list meetings. Status: {}, Body: {}",
                    response.code(), redacted);
                throw new IOException("Failed to list meetings: HTTP " + response.code());
            }

            ZoomMeetingList meetingList = objectMapper.readValue(responseBody, ZoomMeetingList.class);
            log.info("Found {} meetings (page {}/{})",
                meetingList.getCount(), meetingList.getPageNumber(), meetingList.getPageCount());

            return meetingList;
        }
    }

    /**
     * Get details for a specific meeting.
     *
     * Production: With retry logic
     * Security: Join URLs redacted in logs
     *
     * @param meetingId Meeting ID
     * @return Meeting details
     * @throws IOException if API call fails
     */
    public ZoomMeeting getMeeting(String meetingId) throws IOException {
        if (meetingId == null || meetingId.isBlank()) {
            throw new IllegalArgumentException("Meeting ID is required");
        }

        log.info("Fetching meeting: {}", meetingId);

        return RetryUtil.withRetry(
            () -> {
                try {
                    return executeGetMeeting(meetingId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "getMeeting: " + meetingId
        );
    }

    private ZoomMeeting executeGetMeeting(String meetingId) throws IOException {
        String accessToken = oauthService.getAccessToken();
        String url = zoomConfig.getApi().getBaseUrl() + "/meetings/" + meetingId;

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to get meeting. Status: {}, Body: {}",
                    response.code(), redacted);

                if (response.code() == 404) {
                    throw new IOException("Meeting not found: " + meetingId);
                }

                throw new IOException("Failed to get meeting: HTTP " + response.code());
            }

            ZoomMeeting meeting = objectMapper.readValue(responseBody, ZoomMeeting.class);
            log.info("Retrieved meeting: {} - {}", meeting.getId(), meeting.getTopic());

            return meeting;
        }
    }

    /**
     * Get meeting details using user's OAuth token.
     *
     * Production: With retry logic
     * Security: Join URLs redacted in logs
     *
     * @param meetingId Meeting ID
     * @param userAccessToken User's OAuth access token with meeting:read:meeting scope
     * @return Meeting details
     * @throws IOException if API call fails
     */
    public ZoomMeeting getMeetingWithUserToken(String meetingId, String userAccessToken) throws IOException {
        if (meetingId == null || meetingId.isBlank()) {
            throw new IllegalArgumentException("Meeting ID is required");
        }
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalArgumentException("User access token is required");
        }

        log.info("Fetching meeting with user token: {}", meetingId);

        return RetryUtil.withRetry(
            () -> {
                try {
                    return executeGetMeetingWithUserToken(meetingId, userAccessToken);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "getMeetingWithUserToken: " + meetingId
        );
    }

    private ZoomMeeting executeGetMeetingWithUserToken(String meetingId, String userAccessToken) throws IOException {
        String url = zoomConfig.getApi().getBaseUrl() + "/meetings/" + meetingId;

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + userAccessToken)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to get meeting. Status: {}, Body: {}",
                    response.code(), redacted);

                if (response.code() == 404) {
                    throw new IOException("Meeting not found: " + meetingId);
                }

                throw new IOException("Failed to get meeting: HTTP " + response.code());
            }

            ZoomMeeting meeting = objectMapper.readValue(responseBody, ZoomMeeting.class);
            log.info("Retrieved meeting: {} - {}", meeting.getId(), meeting.getTopic());

            return meeting;
        }
    }

    /**
     * Update an existing meeting.
     *
     * Production: Partial updates supported (only send fields to change)
     * Security: OAuth authentication
     *
     * @param meetingId Meeting ID to update
     * @param request Meeting updates (only include fields to change)
     * @throws IOException if API call fails
     */
    public void updateMeeting(String meetingId, ZoomMeetingRequest request) throws IOException {
        if (meetingId == null || meetingId.isBlank()) {
            throw new IllegalArgumentException("Meeting ID is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Meeting request is required");
        }

        log.info("Updating meeting: {}", meetingId);

        RetryUtil.withRetry(
            () -> {
                try {
                    executeUpdateMeeting(meetingId, request);
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "updateMeeting: " + meetingId
        );
    }

    private void executeUpdateMeeting(String meetingId, ZoomMeetingRequest request) throws IOException {
        String accessToken = oauthService.getAccessToken();
        String url = zoomConfig.getApi().getBaseUrl() + "/meetings/" + meetingId;

        String requestBody = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .addHeader("Content-Type", "application/json")
            .patch(RequestBody.create(requestBody, JSON))
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to update meeting. Status: {}, Body: {}",
                    response.code(), redacted);

                if (response.code() == 404) {
                    throw new IOException("Meeting not found: " + meetingId);
                }

                throw new IOException("Failed to update meeting: HTTP " + response.code());
            }

            log.info("Meeting updated successfully: {}", meetingId);
        }
    }

    /**
     * Delete a meeting.
     *
     * Production: Permanent deletion, cannot be undone
     * Security: OAuth authentication
     *
     * @param meetingId Meeting ID to delete
     * @param occurrenceId Optional: Delete specific occurrence of recurring meeting
     * @param scheduleForReminder Send cancellation email to participants (default: false)
     * @throws IOException if API call fails
     */
    public void deleteMeeting(
        String meetingId,
        String occurrenceId,
        Boolean scheduleForReminder
    ) throws IOException {
        if (meetingId == null || meetingId.isBlank()) {
            throw new IllegalArgumentException("Meeting ID is required");
        }

        log.info("Deleting meeting: {}", meetingId);

        RetryUtil.withRetry(
            () -> {
                try {
                    executeDeleteMeeting(meetingId, occurrenceId, scheduleForReminder);
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            RetryUtil.RetryConfig.defaultConfig(),
            "deleteMeeting: " + meetingId
        );
    }

    private void executeDeleteMeeting(
        String meetingId,
        String occurrenceId,
        Boolean scheduleForReminder
    ) throws IOException {
        String accessToken = oauthService.getAccessToken();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(
            zoomConfig.getApi().getBaseUrl() + "/meetings/" + meetingId
        ).newBuilder();

        if (occurrenceId != null && !occurrenceId.isBlank()) {
            urlBuilder.addQueryParameter("occurrence_id", occurrenceId);
        }
        if (scheduleForReminder != null) {
            urlBuilder.addQueryParameter("schedule_for_reminder", scheduleForReminder.toString());
        }

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", "Bearer " + accessToken)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                String redacted = redactionService.redactForLogging(responseBody);
                log.error("Failed to delete meeting. Status: {}, Body: {}",
                    response.code(), redacted);

                if (response.code() == 404) {
                    throw new IOException("Meeting not found: " + meetingId);
                }

                throw new IOException("Failed to delete meeting: HTTP " + response.code());
            }

            log.info("Meeting deleted successfully: {}", meetingId);
        }
    }
}
