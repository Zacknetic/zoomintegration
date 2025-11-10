package com.zacknetic.zoomintegration.zoom.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Zoom meeting response model.
 *
 * Fellow Standards: Production-ready model following Zoom API structure
 * Security: PII (join URLs, passwords) handled by PIIRedactionService in logging
 * Explicit: All fields documented with Zoom API field names
 *
 * Zoom API Reference: https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#operation/meeting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoomMeeting {

    /**
     * Meeting ID (unique identifier)
     * Example: 123456789
     */
    @JsonProperty("id")
    private Long id;

    /**
     * Meeting UUID (unique across all Zoom meetings)
     * Used for recordings and some API calls
     */
    @JsonProperty("uuid")
    private String uuid;

    /**
     * Meeting topic/title
     */
    @JsonProperty("topic")
    private String topic;

    /**
     * Meeting type
     * 1=Instant, 2=Scheduled, 3=Recurring (no fixed time), 8=Recurring (fixed time)
     */
    @JsonProperty("type")
    private Integer type;

    /**
     * Meeting status
     * Values: "waiting", "started", "finished"
     */
    @JsonProperty("status")
    private String status;

    /**
     * Meeting start time in ISO 8601 format
     * Example: "2024-01-15T14:00:00Z"
     */
    @JsonProperty("start_time")
    private String startTime;

    /**
     * Meeting duration in minutes
     */
    @JsonProperty("duration")
    private Integer duration;

    /**
     * Timezone
     * Example: "America/Los_Angeles"
     */
    @JsonProperty("timezone")
    private String timezone;

    /**
     * Meeting agenda/description
     */
    @JsonProperty("agenda")
    private String agenda;

    /**
     * Meeting creation time
     */
    @JsonProperty("created_at")
    private String createdAt;

    /**
     * Join URL for participants
     * Security: Contains meeting access credentials
     */
    @JsonProperty("join_url")
    private String joinUrl;

    /**
     * Start URL for host
     * Security: Contains host credentials - must be kept private
     */
    @JsonProperty("start_url")
    private String startUrl;

    /**
     * Meeting password
     * Security: Required for most meetings per Zoom security settings
     */
    @JsonProperty("password")
    private String password;

    /**
     * H.323/SIP room system password
     */
    @JsonProperty("h323_password")
    private String h323Password;

    /**
     * Encrypted meeting password for third-party endpoints
     */
    @JsonProperty("encrypted_password")
    private String encryptedPassword;

    /**
     * Personal Meeting ID (PMI)
     * Only present if meeting uses host's PMI
     */
    @JsonProperty("pmi")
    private Long pmi;

    /**
     * Tracking fields for analytics
     */
    @JsonProperty("tracking_fields")
    private Object trackingFields;

    /**
     * Meeting occurrence details (for recurring meetings)
     */
    @JsonProperty("occurrences")
    private Object occurrences;

    /**
     * Meeting settings
     */
    @JsonProperty("settings")
    private ZoomMeetingSettings settings;

    /**
     * Meeting host ID
     */
    @JsonProperty("host_id")
    private String hostId;

    /**
     * Meeting host email
     */
    @JsonProperty("host_email")
    private String hostEmail;

    /**
     * Registration URL (if registration is enabled)
     */
    @JsonProperty("registration_url")
    private String registrationUrl;
}
