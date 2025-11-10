package com.zacknetic.zoomintegration.zoom.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Zoom meeting settings model.
 *
 * Fellow Standards: Production-ready model with comprehensive settings
 * Security: All fields validated by Zoom API
 * Explicit: Clear field names and documentation
 *
 * Zoom API Reference: https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#operation/meetingCreate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoomMeetingSettings {

    /**
     * Enable host video when meeting starts
     */
    @JsonProperty("host_video")
    @Builder.Default
    private Boolean hostVideo = true;

    /**
     * Enable participant video when they join
     */
    @JsonProperty("participant_video")
    @Builder.Default
    private Boolean participantVideo = true;

    /**
     * Join meeting before host (only for scheduled or recurring meetings)
     */
    @JsonProperty("join_before_host")
    @Builder.Default
    private Boolean joinBeforeHost = false;

    /**
     * Mute participants upon entry
     */
    @JsonProperty("mute_upon_entry")
    @Builder.Default
    private Boolean muteUponEntry = false;

    /**
     * Enable waiting room
     * Security: Recommended for public meetings
     */
    @JsonProperty("waiting_room")
    @Builder.Default
    private Boolean waitingRoom = true;

    /**
     * Meeting password
     * Security: Zoom enforces password requirements
     */
    @JsonProperty("password")
    private String password;

    /**
     * Automatically record meeting
     * Values: "local", "cloud", "none"
     */
    @JsonProperty("auto_recording")
    @Builder.Default
    private String autoRecording = "none";

    /**
     * Approve or block users from specific regions/countries
     */
    @JsonProperty("approval_type")
    @Builder.Default
    private Integer approvalType = 2; // 0=auto, 1=manual, 2=no registration

    /**
     * Send meeting registration confirmation email
     */
    @JsonProperty("registration_type")
    private Integer registrationType;

    /**
     * Audio options
     * Values: "both", "telephony", "voip"
     */
    @JsonProperty("audio")
    @Builder.Default
    private String audio = "both";

    /**
     * Allow participants to join from multiple devices
     */
    @JsonProperty("alternative_hosts")
    private String alternativeHosts;

    /**
     * Close registration after event date
     */
    @JsonProperty("close_registration")
    private Boolean closeRegistration;

    /**
     * Show social share buttons on registration page
     */
    @JsonProperty("show_share_button")
    private Boolean showShareButton;

    /**
     * Allow participants to join before host (only for scheduled meetings)
     */
    @JsonProperty("allow_multiple_devices")
    private Boolean allowMultipleDevices;

    /**
     * Enable users to select language for email notifications
     */
    @JsonProperty("email_notification")
    private Boolean emailNotification;

    /**
     * Encrypt meeting
     * Security: End-to-end encryption
     */
    @JsonProperty("encryption_type")
    private String encryptionType;

    /**
     * Enable focus mode (participants can only see host and co-hosts)
     */
    @JsonProperty("focus_mode")
    private Boolean focusMode;

    /**
     * Enable meeting chat
     */
    @JsonProperty("meeting_chat")
    @Builder.Default
    private Boolean meetingChat = true;
}
