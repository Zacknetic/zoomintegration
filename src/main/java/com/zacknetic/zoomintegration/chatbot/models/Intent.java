package com.zacknetic.zoomintegration.chatbot.models;

/**
 * Represents an intent that the chatbot can recognize and handle.
 * Intents define what the user wants to accomplish.
 *
 * Security: No PII in intent enums - these are system-level identifiers
 * Production: Explicit enum for type safety and clear intent recognition
 */
public enum Intent {
    // Meeting management intents
    SCHEDULE_MEETING("Schedule a new meeting"),
    LIST_MEETINGS("List upcoming meetings"),
    GET_MEETING("Get specific meeting details"),
    UPDATE_MEETING("Update an existing meeting"),
    DELETE_MEETING("Cancel/delete a meeting"),

    // Recording intents
    LIST_RECORDINGS("List available recordings"),
    GET_RECORDING("Get specific recording details"),
    DOWNLOAD_RECORDING("Download a recording"),

    // User management intents
    GET_USER("Get user information"),
    LIST_USERS("List users in organization"),
    CREATE_USER("Create new user"),

    // General intents
    HELP("Show available commands"),
    GREETING("User greeting"),
    UNKNOWN("Intent not recognized");

    private final String description;

    Intent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if this intent requires authentication to Zoom API
     * Production: Explicit security boundary
     */
    public boolean requiresAuthentication() {
        return this != GREETING && this != HELP && this != UNKNOWN;
    }

    /**
     * Checks if this intent modifies data (write operation)
     * Production: Differentiates read vs write operations for auditing
     */
    public boolean isWriteOperation() {
        return this == SCHEDULE_MEETING
            || this == UPDATE_MEETING
            || this == DELETE_MEETING
            || this == CREATE_USER;
    }
}
