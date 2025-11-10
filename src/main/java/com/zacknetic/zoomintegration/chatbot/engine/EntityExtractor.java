package com.zacknetic.zoomintegration.chatbot.engine;

import com.zacknetic.zoomintegration.chatbot.config.ChatbotConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured entities from natural language text.
 * Entities include: dates, times, emails, names, durations, etc.
 *
 * Production: Regex-based extraction for MVP, extensible for NER models
 * Security: All extracted entities validated before use
 * Fail-fast: Returns empty map rather than null
 */
@Component
public class EntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(EntityExtractor.class);

    private final ChatbotConfiguration config;

    public EntityExtractor(ChatbotConfiguration config) {
        this.config = config;
    }

    // Email pattern (RFC 5322 simplified)
    // Security: Conservative pattern to avoid false positives
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    // Time patterns (24-hour and 12-hour formats)
    private static final Pattern TIME_24H_PATTERN = Pattern.compile(
        "\\b([01]?[0-9]|2[0-3]):([0-5][0-9])\\b"
    );
    private static final Pattern TIME_12H_PATTERN = Pattern.compile(
        "\\b(1[0-2]|0?[1-9]):([0-5][0-9])\\s*(am|pm|AM|PM)\\b"
    );
    private static final Pattern TIME_SIMPLE_PATTERN = Pattern.compile(
        "\\b(1[0-2]|0?[1-9])\\s*(am|pm|AM|PM)\\b"
    );

    // Date patterns
    private static final Pattern DATE_ISO_PATTERN = Pattern.compile(
        "\\b(\\d{4})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])\\b"
    );
    private static final Pattern DATE_SLASH_PATTERN = Pattern.compile(
        "\\b(0?[1-9]|1[0-2])/(0?[1-9]|[12][0-9]|3[01])/(\\d{2,4})\\b"
    );
    
    // Month name patterns (e.g., "november 4th", "nov 4", "january 15")
    private static final Pattern DATE_MONTH_NAME_PATTERN = Pattern.compile(
        "\\b(january|february|march|april|may|june|july|august|september|october|november|december|" +
        "jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\\s+" +
        "(\\d{1,2})(st|nd|rd|th)?\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Duration patterns (e.g., "30 minutes", "1 hour")
    private static final Pattern DURATION_PATTERN = Pattern.compile(
        "\\b(\\d+)\\s*(minute|minutes|min|mins|hour|hours|hr|hrs)\\b",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts all entities from the given message.
     *
     * @param message The user's message
     * @return Map of entity type to extracted value
     *
     * Production: Never returns null, returns empty map if no entities found
     * Explicit: Clear entity type keys
     */
    public Map<String, String> extractEntities(String message) {
        if (message == null || message.isBlank()) {
            log.debug("Empty message for entity extraction");
            return new HashMap<>();
        }

        Map<String, String> entities = new HashMap<>();

        // Extract emails
        extractEmails(message, entities);

        // Extract dates
        extractDates(message, entities);

        // Extract times
        extractTimes(message, entities);

        // Extract durations
        extractDurations(message, entities);

        // Extract relative dates (tomorrow, today, next week, etc.)
        extractRelativeDates(message, entities);

        if (!entities.isEmpty()) {
            log.info("Extracted {} entities from message", entities.size());
        }

        return entities;
    }

    /**
     * Extract email addresses from message.
     * Security: Validates email format before extraction
     * Fellow Golden Rule: No magic numbers - max length from configuration
     */
    private void extractEmails(String message, Map<String, String> entities) {
        Matcher matcher = EMAIL_PATTERN.matcher(message);
        if (matcher.find()) {
            String email = matcher.group();

            int maxEmailLength = config.getEntityExtractor().getMaxEmailLength();

            // Additional validation: basic sanity check
            if (email.length() <= maxEmailLength && email.split("@").length == 2) {
                entities.put("email", email);
                log.debug("Extracted email entity");
            }
        }
    }

    /**
     * Extract dates from message.
     * Production: Handles multiple date formats including month names
     */
    private void extractDates(String message, Map<String, String> entities) {
        // Try ISO format first (YYYY-MM-DD)
        Matcher isoMatcher = DATE_ISO_PATTERN.matcher(message);
        if (isoMatcher.find()) {
            String dateStr = isoMatcher.group();
            try {
                LocalDate date = LocalDate.parse(dateStr);
                entities.put("date", date.toString());
                log.debug("Extracted ISO date: {}", date);
                return;
            } catch (DateTimeParseException e) {
                log.debug("Invalid ISO date format: {}", dateStr);
            }
        }

        // Try month name format (e.g., "november 4th", "nov 4")
        Matcher monthNameMatcher = DATE_MONTH_NAME_PATTERN.matcher(message);
        if (monthNameMatcher.find()) {
            String monthName = monthNameMatcher.group(1).toLowerCase();
            String day = monthNameMatcher.group(2);
            
            try {
                int month = parseMonthName(monthName);
                int dayInt = Integer.parseInt(day);
                
                // Determine year: use current year, or next year if date has passed
                int currentYear = LocalDate.now().getYear();
                LocalDate candidateDate = LocalDate.of(currentYear, month, dayInt);
                
                // If the date is in the past and no year was explicitly mentioned, use next year
                if (candidateDate.isBefore(LocalDate.now())) {
                    candidateDate = LocalDate.of(currentYear + 1, month, dayInt);
                    log.debug("Date {} is in the past, using next year: {}", 
                        monthName + " " + day, candidateDate);
                }
                
                entities.put("date", candidateDate.toString());
                log.debug("Extracted month name date: {}", candidateDate);
                return;
            } catch (Exception e) {
                log.debug("Invalid month name date format: {} {}", monthName, day);
            }
        }

        // Try MM/DD/YYYY format
        Matcher slashMatcher = DATE_SLASH_PATTERN.matcher(message);
        if (slashMatcher.find()) {
            String month = slashMatcher.group(1);
            String day = slashMatcher.group(2);
            String year = slashMatcher.group(3);

            // Handle 2-digit year
            // Fellow Golden Rule: No magic numbers - threshold from configuration
            if (year.length() == 2) {
                int yearInt = Integer.parseInt(year);
                int threshold = config.getEntityExtractor().getTwoDigitYearThreshold();
                year = String.valueOf(yearInt < threshold ? 2000 + yearInt : 1900 + yearInt);
            }

            try {
                String dateStr = String.format("%s-%02d-%02d",
                    year, Integer.parseInt(month), Integer.parseInt(day));
                LocalDate date = LocalDate.parse(dateStr);
                entities.put("date", date.toString());
                log.debug("Extracted slash date: {}", date);
            } catch (Exception e) {
                log.debug("Invalid slash date format");
            }
        }
    }

    /**
     * Extract times from message.
     * Production: Handles 12-hour and 24-hour formats
     */
    private void extractTimes(String message, Map<String, String> entities) {
        // Try 24-hour format first
        Matcher time24Matcher = TIME_24H_PATTERN.matcher(message);
        if (time24Matcher.find()) {
            String timeStr = time24Matcher.group();
            try {
                LocalTime time = LocalTime.parse(timeStr);
                entities.put("time", time.toString());
                log.debug("Extracted 24h time: {}", time);
                return;
            } catch (DateTimeParseException e) {
                log.debug("Invalid 24h time format: {}", timeStr);
            }
        }

        // Try 12-hour format
        Matcher time12Matcher = TIME_12H_PATTERN.matcher(message);
        if (time12Matcher.find()) {
            String hour = time12Matcher.group(1);
            String minute = time12Matcher.group(2);
            String period = time12Matcher.group(3).toUpperCase();

            try {
                String timeStr = String.format("%s:%s %s", hour, minute, period);
                LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("h:mm a"));
                entities.put("time", time.toString());
                log.debug("Extracted 12h time: {}", time);
                return;
            } catch (DateTimeParseException e) {
                log.debug("Invalid 12h time format");
            }
        }

        // Try simple format (e.g., "2pm")
        Matcher simpleTimeMatcher = TIME_SIMPLE_PATTERN.matcher(message);
        if (simpleTimeMatcher.find()) {
            String hour = simpleTimeMatcher.group(1);
            String period = simpleTimeMatcher.group(2).toUpperCase();

            try {
                String timeStr = String.format("%s:00 %s", hour, period);
                LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("h:mm a"));
                entities.put("time", time.toString());
                log.debug("Extracted simple time: {}", time);
            } catch (DateTimeParseException e) {
                log.debug("Invalid simple time format");
            }
        }
    }

    /**
     * Extract duration from message (e.g., "30 minutes", "1 hour").
     * Production: Converts all durations to minutes for consistency
     */
    private void extractDurations(String message, Map<String, String> entities) {
        Matcher matcher = DURATION_PATTERN.matcher(message);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            int minutes;
            if (unit.startsWith("hour") || unit.startsWith("hr")) {
                minutes = value * 60;
            } else {
                minutes = value;
            }

            entities.put("duration", String.valueOf(minutes));
            log.debug("Extracted duration: {} minutes", minutes);
        }
    }

    /**
     * Extract relative dates (tomorrow, today, next week, etc.).
     * Production: Converts relative dates to absolute dates for Zoom API
     */
    private void extractRelativeDates(String message, Map<String, String> entities) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("today")) {
            entities.put("date", LocalDate.now().toString());
            log.debug("Extracted relative date: today");
        } else if (lowerMessage.contains("tomorrow")) {
            entities.put("date", LocalDate.now().plusDays(1).toString());
            log.debug("Extracted relative date: tomorrow");
        } else if (lowerMessage.matches(".*\\bnext\\s+monday\\b.*")) {
            entities.put("date", getNextDayOfWeek(1).toString());
            log.debug("Extracted relative date: next Monday");
        } else if (lowerMessage.matches(".*\\bnext\\s+tuesday\\b.*")) {
            entities.put("date", getNextDayOfWeek(2).toString());
            log.debug("Extracted relative date: next Tuesday");
        } else if (lowerMessage.matches(".*\\bnext\\s+wednesday\\b.*")) {
            entities.put("date", getNextDayOfWeek(3).toString());
            log.debug("Extracted relative date: next Wednesday");
        } else if (lowerMessage.matches(".*\\bnext\\s+thursday\\b.*")) {
            entities.put("date", getNextDayOfWeek(4).toString());
            log.debug("Extracted relative date: next Thursday");
        } else if (lowerMessage.matches(".*\\bnext\\s+friday\\b.*")) {
            entities.put("date", getNextDayOfWeek(5).toString());
            log.debug("Extracted relative date: next Friday");
        } else if (lowerMessage.matches(".*\\bnext\\s+week\\b.*")) {
            entities.put("date", LocalDate.now().plusWeeks(1).toString());
            log.debug("Extracted relative date: next week");
        }
    }

    /**
     * Get the date of the next occurrence of a specific day of week.
     * Production: Helper method for relative date extraction
     *
     * @param targetDayOfWeek 1=Monday, 7=Sunday
     */
    private LocalDate getNextDayOfWeek(int targetDayOfWeek) {
        LocalDate today = LocalDate.now();
        int currentDayOfWeek = today.getDayOfWeek().getValue();

        int daysToAdd;
        if (targetDayOfWeek > currentDayOfWeek) {
            daysToAdd = targetDayOfWeek - currentDayOfWeek;
        } else {
            daysToAdd = 7 - currentDayOfWeek + targetDayOfWeek;
        }

        return today.plusDays(daysToAdd);
    }

    /**
     * Parses month name (full or abbreviated) to month number.
     * 
     * @param monthName Month name (e.g., "january", "jan")
     * @return Month number (1-12)
     */
    private int parseMonthName(String monthName) {
        return switch (monthName.toLowerCase()) {
            case "january", "jan" -> 1;
            case "february", "feb" -> 2;
            case "march", "mar" -> 3;
            case "april", "apr" -> 4;
            case "may" -> 5;
            case "june", "jun" -> 6;
            case "july", "jul" -> 7;
            case "august", "aug" -> 8;
            case "september", "sep", "sept" -> 9;
            case "october", "oct" -> 10;
            case "november", "nov" -> 11;
            case "december", "dec" -> 12;
            default -> throw new IllegalArgumentException("Invalid month name: " + monthName);
        };
    }

    /**
     * Validates extracted entities.
     * Production: Ensures entities are safe to use with Zoom API
     *
     * @param entities The extracted entities
     * @return true if all entities are valid
     */
    public boolean validateEntities(Map<String, String> entities) {
        if (entities == null || entities.isEmpty()) {
            return true; // Empty is valid
        }

        // Validate date is not in the past (for meeting scheduling)
        if (entities.containsKey("date")) {
            try {
                LocalDate date = LocalDate.parse(entities.get("date"));
                if (date.isBefore(LocalDate.now())) {
                    log.warn("Extracted date is in the past: {}", date);
                    return false;
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid date format in entities: {}", entities.get("date"));
                return false;
            }
        }

        // Validate time format
        if (entities.containsKey("time")) {
            try {
                LocalTime.parse(entities.get("time"));
            } catch (DateTimeParseException e) {
                log.warn("Invalid time format in entities: {}", entities.get("time"));
                return false;
            }
        }

        // Validate duration is reasonable
        // Fellow Golden Rule: No magic numbers - range from configuration
        if (entities.containsKey("duration")) {
            try {
                int duration = Integer.parseInt(entities.get("duration"));
                int minDuration = config.getEntityExtractor().getMinDurationMinutes();
                int maxDuration = config.getEntityExtractor().getMaxDurationMinutes();

                if (duration < minDuration || duration > maxDuration) {
                    log.warn("Duration out of valid range: {} minutes (valid: {}-{})",
                        duration, minDuration, maxDuration);
                    return false;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid duration format in entities: {}", entities.get("duration"));
                return false;
            }
        }

        return true;
    }
}
