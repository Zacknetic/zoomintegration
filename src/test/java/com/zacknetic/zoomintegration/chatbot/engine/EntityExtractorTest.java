package com.zacknetic.zoomintegration.chatbot.engine;

import com.zacknetic.zoomintegration.chatbot.config.ChatbotConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EntityExtractor.
 *
 * Fellow Golden Rule: Real tests that verify specific behavior and can catch real problems
 * Production: Comprehensive coverage of all entity types
 * Fail-fast: Tests fail if extraction is incorrect
 */
class EntityExtractorTest {

    private EntityExtractor extractor;
    private ChatbotConfiguration config;

    @BeforeEach
    void setUp() {
        // Initialize configuration with default values
        config = new ChatbotConfiguration();
        extractor = new EntityExtractor(config);
    }

    // Email extraction tests
    @Test
    void extractEntities_shouldExtractEmail_whenMessageContainsEmail() {
        Map<String, String> entities = extractor.extractEntities(
            "Schedule a meeting with john@example.com"
        );

        assertThat(entities).containsEntry("email", "john@example.com");
    }

    @Test
    void extractEntities_shouldExtractEmail_withPlusSign() {
        Map<String, String> entities = extractor.extractEntities(
            "Add user test+demo@example.com"
        );

        assertThat(entities).containsEntry("email", "test+demo@example.com");
    }

    @Test
    void extractEntities_shouldExtractEmail_withSubdomain() {
        Map<String, String> entities = extractor.extractEntities(
            "Contact jane@mail.example.com"
        );

        assertThat(entities).containsEntry("email", "jane@mail.example.com");
    }

    // Date extraction tests - ISO format
    @Test
    void extractEntities_shouldExtractDate_whenISOFormatProvided() {
        Map<String, String> entities = extractor.extractEntities(
            "Schedule meeting on 2024-01-15"
        );

        assertThat(entities).containsEntry("date", "2024-01-15");
    }

    // Date extraction tests - slash format
    @Test
    void extractEntities_shouldExtractDate_whenSlashFormatProvided() {
        Map<String, String> entities = extractor.extractEntities(
            "Book a room for 1/15/2024"
        );

        assertThat(entities).containsEntry("date", "2024-01-15");
    }

    @Test
    void extractEntities_shouldExtractDate_withTwoDigitYear() {
        Map<String, String> entities = extractor.extractEntities(
            "Meeting on 1/15/24"
        );

        assertThat(entities).containsEntry("date", "2024-01-15");
    }

    // Relative date extraction tests
    @Test
    void extractEntities_shouldExtractDate_whenMessageSaysToday() {
        Map<String, String> entities = extractor.extractEntities(
            "Schedule meeting today"
        );

        assertThat(entities).containsKey("date");
        assertThat(entities.get("date")).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void extractEntities_shouldExtractDate_whenMessageSaysTomorrow() {
        Map<String, String> entities = extractor.extractEntities(
            "Schedule meeting tomorrow"
        );

        assertThat(entities).containsKey("date");
        assertThat(entities.get("date")).isEqualTo(LocalDate.now().plusDays(1).toString());
    }

    @Test
    void extractEntities_shouldExtractDate_whenMessageSaysNextMonday() {
        Map<String, String> entities = extractor.extractEntities(
            "Book conference next Monday"
        );

        assertThat(entities).containsKey("date");
        // Verify it's a valid date
        assertThat(entities.get("date")).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void extractEntities_shouldExtractDate_whenMessageSaysNextWeek() {
        Map<String, String> entities = extractor.extractEntities(
            "Meeting next week"
        );

        assertThat(entities).containsKey("date");
        assertThat(entities.get("date")).isEqualTo(LocalDate.now().plusWeeks(1).toString());
    }

    // Time extraction tests - 24-hour format
    @Test
    void extractEntities_shouldExtractTime_when24HourFormatProvided() {
        Map<String, String> entities = extractor.extractEntities(
            "Schedule meeting at 14:00"
        );

        assertThat(entities).containsEntry("time", "14:00");
    }

    @Test
    void extractEntities_shouldNotExtractTime_withAmbiguousFormat() {
        // "9:30" without AM/PM is ambiguous - could be 9:30 AM or 9:30 (24-hour)
        // Production: Require explicit format to avoid confusion
        Map<String, String> entities = extractor.extractEntities(
            "Meeting at 9:30"
        );

        // Should not extract without AM/PM designation
        assertThat(entities).doesNotContainKey("time");
    }

    // Time extraction tests - 12-hour format
    @Test
    void extractEntities_shouldExtractTime_when12HourFormatProvided() {
        Map<String, String> entities = extractor.extractEntities(
            "Schedule meeting at 2:30 PM"
        );

        assertThat(entities).containsEntry("time", "14:30");
    }

    @Test
    void extractEntities_shouldExtractTime_withAMDesignation() {
        Map<String, String> entities = extractor.extractEntities(
            "Book room at 10:00 AM"
        );

        assertThat(entities).containsEntry("time", "10:00");
    }

    @Test
    void extractEntities_shouldExtractTime_withLowercaseAM() {
        Map<String, String> entities = extractor.extractEntities(
            "Meeting at 9:00 am"
        );

        assertThat(entities).containsEntry("time", "09:00");
    }

    // Time extraction tests - simple format
    @Test
    void extractEntities_shouldExtractTime_whenSimpleFormatProvided() {
        Map<String, String> entities = extractor.extractEntities(
            "Meeting at 2pm"
        );

        assertThat(entities).containsEntry("time", "14:00");
    }

    @Test
    void extractEntities_shouldExtractTime_withSimpleAMFormat() {
        Map<String, String> entities = extractor.extractEntities(
            "Call at 9am"
        );

        assertThat(entities).containsEntry("time", "09:00");
    }

    // Duration extraction tests
    @Test
    void extractEntities_shouldExtractDuration_whenMinutesSpecified() {
        Map<String, String> entities = extractor.extractEntities(
            "Book room for 30 minutes"
        );

        assertThat(entities).containsEntry("duration", "30");
    }

    @Test
    void extractEntities_shouldExtractDuration_whenHoursSpecified() {
        Map<String, String> entities = extractor.extractEntities(
            "Meeting duration 2 hours"
        );

        assertThat(entities).containsEntry("duration", "120"); // Converted to minutes
    }

    @Test
    void extractEntities_shouldExtractDuration_withMinAbbreviation() {
        Map<String, String> entities = extractor.extractEntities(
            "15 min call"
        );

        assertThat(entities).containsEntry("duration", "15");
    }

    @Test
    void extractEntities_shouldExtractDuration_withHrAbbreviation() {
        Map<String, String> entities = extractor.extractEntities(
            "1 hr meeting"
        );

        assertThat(entities).containsEntry("duration", "60");
    }

    // Multiple entities extraction tests
    @Test
    void extractEntities_shouldExtractMultipleEntities_whenPresentInMessage() {
        Map<String, String> entities = extractor.extractEntities(
            "Schedule meeting with john@example.com tomorrow at 2pm for 30 minutes"
        );

        assertThat(entities).containsEntry("email", "john@example.com");
        assertThat(entities).containsKey("date");
        assertThat(entities).containsEntry("time", "14:00");
        assertThat(entities).containsEntry("duration", "30");
        assertThat(entities).hasSize(4);
    }

    @Test
    void extractEntities_shouldExtractMultipleEntities_withISODate() {
        Map<String, String> entities = extractor.extractEntities(
            "Book conference on 2024-02-20 at 14:30 with team@example.com"
        );

        assertThat(entities).containsEntry("date", "2024-02-20");
        assertThat(entities).containsEntry("time", "14:30");
        assertThat(entities).containsEntry("email", "team@example.com");
    }

    // Edge case tests
    @Test
    void extractEntities_shouldReturnEmptyMap_whenMessageIsNull() {
        Map<String, String> entities = extractor.extractEntities(null);

        assertThat(entities).isEmpty();
    }

    @Test
    void extractEntities_shouldReturnEmptyMap_whenMessageIsEmpty() {
        Map<String, String> entities = extractor.extractEntities("");

        assertThat(entities).isEmpty();
    }

    @Test
    void extractEntities_shouldReturnEmptyMap_whenMessageIsBlank() {
        Map<String, String> entities = extractor.extractEntities("   ");

        assertThat(entities).isEmpty();
    }

    @Test
    void extractEntities_shouldExtractDateForToday_evenInRegularSentence() {
        // Production: "today" is recognized as a date entity even in regular text
        // This is intentional for NLP flexibility
        Map<String, String> entities = extractor.extractEntities(
            "Hello how are you doing today"
        );

        assertThat(entities).containsEntry("date", LocalDate.now().toString());
    }

    // Validation tests
    @Test
    void validateEntities_shouldReturnTrue_whenEntitiesAreValid() {
        Map<String, String> entities = Map.of(
            "date", LocalDate.now().plusDays(1).toString(),
            "time", "14:00",
            "duration", "60"
        );

        boolean valid = extractor.validateEntities(entities);

        assertThat(valid).isTrue();
    }

    @Test
    void validateEntities_shouldReturnFalse_whenDateIsInPast() {
        Map<String, String> entities = Map.of(
            "date", LocalDate.now().minusDays(1).toString()
        );

        boolean valid = extractor.validateEntities(entities);

        assertThat(valid).isFalse();
    }

    @Test
    void validateEntities_shouldReturnFalse_whenTimeFormatIsInvalid() {
        Map<String, String> entities = Map.of(
            "time", "invalid-time"
        );

        boolean valid = extractor.validateEntities(entities);

        assertThat(valid).isFalse();
    }

    @Test
    void validateEntities_shouldReturnFalse_whenDurationIsTooShort() {
        Map<String, String> entities = Map.of(
            "duration", "0"
        );

        boolean valid = extractor.validateEntities(entities);

        assertThat(valid).isFalse();
    }

    @Test
    void validateEntities_shouldReturnFalse_whenDurationIsTooLong() {
        Map<String, String> entities = Map.of(
            "duration", "2000" // More than 24 hours
        );

        boolean valid = extractor.validateEntities(entities);

        assertThat(valid).isFalse();
    }

    @Test
    void validateEntities_shouldReturnTrue_whenEntitiesAreEmpty() {
        Map<String, String> entities = Map.of();

        boolean valid = extractor.validateEntities(entities);

        assertThat(valid).isTrue();
    }

    @Test
    void validateEntities_shouldReturnTrue_whenEntitiesAreNull() {
        boolean valid = extractor.validateEntities(null);

        assertThat(valid).isTrue();
    }

    // Email validation edge cases
    @Test
    void extractEntities_shouldNotExtractInvalidEmail_withMissingAt() {
        Map<String, String> entities = extractor.extractEntities(
            "Contact user.example.com"
        );

        assertThat(entities).doesNotContainKey("email");
    }

    @Test
    void extractEntities_shouldNotExtractInvalidEmail_withMissingDomain() {
        Map<String, String> entities = extractor.extractEntities(
            "Email user@"
        );

        assertThat(entities).doesNotContainKey("email");
    }
}
