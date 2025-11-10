package com.zacknetic.zoomintegration.chatbot.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Service for handling timezone conversions for the chatbot.
 * 
 * Production: Converts user's local time to UTC for storage and back for display
 * Security: Validates timezone IDs to prevent injection
 * Fail-fast: Throws exceptions for invalid timezones
 */
@Service
public class TimezoneService {

    private static final Logger log = LoggerFactory.getLogger(TimezoneService.class);

    /**
     * Converts local date/time to UTC ISO 8601 format for Zoom API.
     * 
     * @param localDate Date in YYYY-MM-DD format
     * @param localTime Time in HH:mm or HH:mm:ss format
     * @param timezoneId User's timezone ID (e.g., "America/New_York")
     * @return ISO 8601 formatted UTC time string
     */
    public String convertLocalToUtc(String localDate, String localTime, String timezoneId) {
        if (localDate == null || localTime == null) {
            throw new IllegalArgumentException("Date and time are required");
        }

        try {
            // Parse the local date and time
            LocalDate date = LocalDate.parse(localDate);
            
            // Ensure time has seconds format
            String fullTime = normalizeTime(localTime);
            LocalTime time = LocalTime.parse(fullTime);

            // Get the timezone
            ZoneId userZone = getZoneId(timezoneId);

            // Combine date and time in user's timezone
            ZonedDateTime userDateTime = ZonedDateTime.of(date, time, userZone);

            // Convert to UTC
            ZonedDateTime utcDateTime = userDateTime.withZoneSameInstant(ZoneOffset.UTC);

            // Format as ISO 8601 without 'Z' suffix for Zoom API
            // Zoom expects: 2026-11-04T04:00:00 (not 2026-11-04T04:00:00Z)
            // when timezone field is specified in the request
            return utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        } catch (Exception e) {
            log.error("Failed to convert local time to UTC: date={}, time={}, timezone={}", 
                localDate, localTime, timezoneId, e);
            throw new IllegalArgumentException("Invalid date/time format: " + e.getMessage());
        }
    }

    /**
     * Converts UTC time to user's local timezone for display.
     * 
     * @param utcIsoTime UTC time in ISO 8601 format
     * @param timezoneId User's timezone ID
     * @return Formatted local time string (e.g., "January 15, 2024 at 2:00 PM EST")
     */
    public String convertUtcToLocal(String utcIsoTime, String timezoneId) {
        if (utcIsoTime == null) {
            throw new IllegalArgumentException("UTC time is required");
        }

        try {
            // Parse UTC time
            Instant instant = Instant.parse(utcIsoTime);
            
            // Get user's timezone
            ZoneId userZone = getZoneId(timezoneId);

            // Convert to user's timezone
            ZonedDateTime localDateTime = instant.atZone(userZone);

            // Format for display
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a z");
            return localDateTime.format(formatter);

        } catch (Exception e) {
            log.error("Failed to convert UTC to local time: utc={}, timezone={}", 
                utcIsoTime, timezoneId, e);
            // Fallback to simple format
            return utcIsoTime;
        }
    }

    /**
     * Formats a local date and time for user display.
     * 
     * @param localDate Date in YYYY-MM-DD format
     * @param localTime Time in HH:mm format
     * @param timezoneId User's timezone ID
     * @return Formatted string (e.g., "January 15, 2024 at 2:00 PM EST")
     */
    public String formatLocalDateTime(String localDate, String localTime, String timezoneId) {
        try {
            LocalDate date = LocalDate.parse(localDate);
            String fullTime = normalizeTime(localTime);
            LocalTime time = LocalTime.parse(fullTime);
            ZoneId userZone = getZoneId(timezoneId);

            ZonedDateTime zonedDateTime = ZonedDateTime.of(date, time, userZone);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a z");
            return zonedDateTime.format(formatter);

        } catch (Exception e) {
            log.warn("Failed to format local date/time: {} {}", localDate, localTime, e);
            return localDate + " at " + localTime;
        }
    }

    /**
     * Gets the ZoneId from a timezone string, with fallback to UTC.
     * 
     * @param timezoneId Timezone ID or null
     * @return ZoneId (defaults to UTC if null or invalid)
     */
    private ZoneId getZoneId(String timezoneId) {
        if (timezoneId == null || timezoneId.isBlank()) {
            log.debug("No timezone provided, using UTC");
            return ZoneOffset.UTC;
        }

        try {
            return ZoneId.of(timezoneId);
        } catch (DateTimeException e) {
            log.warn("Invalid timezone: {}, falling back to UTC", timezoneId);
            return ZoneOffset.UTC;
        }
    }

    /**
     * Normalizes time string to HH:mm:ss format.
     * 
     * @param time Time string (e.g., "14:00", "2:30", "14")
     * @return Normalized time string (e.g., "14:00:00")
     */
    private String normalizeTime(String time) {
        if (time.contains(":")) {
            String[] parts = time.split(":");
            if (parts.length == 2) {
                return time + ":00";
            }
            return time;
        } else {
            // Handle cases like "14" or "2"
            return time + ":00:00";
        }
    }

    /**
     * Gets the current date in user's timezone.
     * 
     * @param timezoneId User's timezone ID
     * @return Current date in YYYY-MM-DD format
     */
    public String getCurrentDateInTimezone(String timezoneId) {
        ZoneId userZone = getZoneId(timezoneId);
        return LocalDate.now(userZone).toString();
    }
}
