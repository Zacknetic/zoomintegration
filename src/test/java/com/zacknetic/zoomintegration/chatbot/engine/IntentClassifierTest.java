package com.zacknetic.zoomintegration.chatbot.engine;

import com.zacknetic.zoomintegration.chatbot.config.ChatbotConfiguration;
import com.zacknetic.zoomintegration.chatbot.models.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IntentClassifier.
 *
 * Fellow Golden Rule: Real tests that verify specific behavior and can catch real problems
 * Production: Comprehensive coverage of all intent patterns
 * Fail-fast: Tests fail if classification is incorrect
 */
class IntentClassifierTest {

    private IntentClassifier classifier;
    private ChatbotConfiguration config;

    @BeforeEach
    void setUp() {
        // Initialize configuration with default values
        config = new ChatbotConfiguration();
        classifier = new IntentClassifier(config);
    }

    // Greeting intent tests
    @Test
    void classify_shouldRecognizeGreetingIntent_whenUserSaysHello() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("Hello");

        assertThat(result.getIntent()).isEqualTo(Intent.GREETING);
        assertThat(result.getConfidence()).isGreaterThan(0.6);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeGreetingIntent_whenUserSaysHi() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("Hi there!");

        assertThat(result.getIntent()).isEqualTo(Intent.GREETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeGreetingIntent_whenUserSaysGoodMorning() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("Good morning");

        assertThat(result.getIntent()).isEqualTo(Intent.GREETING);
        assertThat(result.isConfident()).isTrue();
    }

    // Help intent tests
    @Test
    void classify_shouldRecognizeHelpIntent_whenUserAsksForHelp() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("help");

        assertThat(result.getIntent()).isEqualTo(Intent.HELP);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeHelpIntent_whenUserAsksWhatCanYouDo() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("What can you do?");

        assertThat(result.getIntent()).isEqualTo(Intent.HELP);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeHelpIntent_whenUserAsksForCommands() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("Show me the commands");

        assertThat(result.getIntent()).isEqualTo(Intent.HELP);
        assertThat(result.isConfident()).isTrue();
    }

    // Schedule meeting intent tests
    @Test
    void classify_shouldRecognizeScheduleMeetingIntent_whenUserSaysScheduleMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Schedule a meeting tomorrow at 2pm");

        assertThat(result.getIntent()).isEqualTo(Intent.SCHEDULE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeScheduleMeetingIntent_whenUserSaysCreateMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Create a meeting for next Monday");

        assertThat(result.getIntent()).isEqualTo(Intent.SCHEDULE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeScheduleMeetingIntent_whenUserSaysSetUpMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Set up a call with john@example.com");

        assertThat(result.getIntent()).isEqualTo(Intent.SCHEDULE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeScheduleMeetingIntent_whenUserSaysBookConference() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Book a conference for 3pm today");

        assertThat(result.getIntent()).isEqualTo(Intent.SCHEDULE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    // List meetings intent tests
    @Test
    void classify_shouldRecognizeListMeetingsIntent_whenUserSaysListMeetings() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("List my meetings");

        assertThat(result.getIntent()).isEqualTo(Intent.LIST_MEETINGS);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeListMeetingsIntent_whenUserSaysShowMeetings() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Show me my meetings this week");

        assertThat(result.getIntent()).isEqualTo(Intent.LIST_MEETINGS);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeListMeetingsIntent_whenUserAsksWhatMeetings() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("What meetings do I have today?");

        assertThat(result.getIntent()).isEqualTo(Intent.LIST_MEETINGS);
        assertThat(result.isConfident()).isTrue();
    }

    // Get meeting intent tests
    @Test
    void classify_shouldRecognizeGetMeetingIntent_whenUserAsksForMeetingDetails() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Get meeting details for 12345");

        assertThat(result.getIntent()).isEqualTo(Intent.GET_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeGetMeetingIntent_whenUserAsksTellMeAboutMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Tell me about the meeting");

        assertThat(result.getIntent()).isEqualTo(Intent.GET_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    // Update meeting intent tests
    @Test
    void classify_shouldRecognizeUpdateMeetingIntent_whenUserSaysRescheduleMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Reschedule the meeting to 3pm");

        assertThat(result.getIntent()).isEqualTo(Intent.UPDATE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeUpdateMeetingIntent_whenUserSaysUpdateMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Update meeting 12345");

        assertThat(result.getIntent()).isEqualTo(Intent.UPDATE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeUpdateMeetingIntent_whenUserSaysChangeTime() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Change the time of the meeting");

        assertThat(result.getIntent()).isEqualTo(Intent.UPDATE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    // Delete meeting intent tests
    @Test
    void classify_shouldRecognizeDeleteMeetingIntent_whenUserSaysCancelMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Cancel the meeting");

        assertThat(result.getIntent()).isEqualTo(Intent.DELETE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeDeleteMeetingIntent_whenUserSaysDeleteMeeting() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Delete meeting 12345");

        assertThat(result.getIntent()).isEqualTo(Intent.DELETE_MEETING);
        assertThat(result.isConfident()).isTrue();
    }

    // Recording intent tests
    @Test
    void classify_shouldRecognizeListRecordingsIntent_whenUserSaysListRecordings() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("List my recordings");

        assertThat(result.getIntent()).isEqualTo(Intent.LIST_RECORDINGS);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeGetRecordingIntent_whenUserAsksForRecordingInfo() {
        // Use "show" or "find" rather than "get" to avoid matching download pattern
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Show recording details");

        assertThat(result.getIntent()).isEqualTo(Intent.GET_RECORDING);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeDownloadRecordingIntent_whenUserSaysDownloadRecording() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Download the recording");

        assertThat(result.getIntent()).isEqualTo(Intent.DOWNLOAD_RECORDING);
        assertThat(result.isConfident()).isTrue();
    }

    // User intent tests
    @Test
    void classify_shouldRecognizeGetUserIntent_whenUserAsksForUserInfo() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Get user john@example.com");

        assertThat(result.getIntent()).isEqualTo(Intent.GET_USER);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeListUsersIntent_whenUserSaysListUsers() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("List all users");

        assertThat(result.getIntent()).isEqualTo(Intent.LIST_USERS);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void classify_shouldRecognizeCreateUserIntent_whenUserSaysCreateUser() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("Create a new user");

        assertThat(result.getIntent()).isEqualTo(Intent.CREATE_USER);
        assertThat(result.isConfident()).isTrue();
    }

    // Unknown intent tests
    @Test
    void classify_shouldReturnUnknownIntent_whenMessageDoesNotMatchAnyPattern() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("xyz foo bar baz random text");

        assertThat(result.getIntent()).isEqualTo(Intent.UNKNOWN);
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(result.isConfident()).isFalse();
    }

    // Edge case tests
    @Test
    void classify_shouldReturnUnknownIntent_whenMessageIsNull() {
        IntentClassifier.IntentClassificationResult result = classifier.classify(null);

        assertThat(result.getIntent()).isEqualTo(Intent.UNKNOWN);
        assertThat(result.getConfidence()).isEqualTo(0.0);
    }

    @Test
    void classify_shouldReturnUnknownIntent_whenMessageIsEmpty() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("");

        assertThat(result.getIntent()).isEqualTo(Intent.UNKNOWN);
        assertThat(result.getConfidence()).isEqualTo(0.0);
    }

    @Test
    void classify_shouldReturnUnknownIntent_whenMessageIsBlank() {
        IntentClassifier.IntentClassificationResult result = classifier.classify("   ");

        assertThat(result.getIntent()).isEqualTo(Intent.UNKNOWN);
        assertThat(result.getConfidence()).isEqualTo(0.0);
    }

    // Case insensitivity tests
    @Test
    void classify_shouldBeCaseInsensitive_whenClassifyingIntents() {
        IntentClassifier.IntentClassificationResult lowerResult =
            classifier.classify("schedule a meeting");
        IntentClassifier.IntentClassificationResult upperResult =
            classifier.classify("SCHEDULE A MEETING");
        IntentClassifier.IntentClassificationResult mixedResult =
            classifier.classify("ScHeDuLe A MeEtInG");

        assertThat(lowerResult.getIntent()).isEqualTo(Intent.SCHEDULE_MEETING);
        assertThat(upperResult.getIntent()).isEqualTo(Intent.SCHEDULE_MEETING);
        assertThat(mixedResult.getIntent()).isEqualTo(Intent.SCHEDULE_MEETING);
    }

    // Confidence score tests
    @Test
    void classify_shouldHaveHighConfidence_whenMessageIsShortAndSpecific() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("help");

        assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void classify_shouldHaveLowerConfidence_whenMessageIsLong() {
        IntentClassifier.IntentClassificationResult result =
            classifier.classify("I was wondering if you could possibly help me schedule a meeting " +
                "for tomorrow afternoon at around 2pm with my team members");

        assertThat(result.getConfidence()).isLessThan(0.90);
    }

    // IntentClassificationResult tests
    @Test
    void intentClassificationResult_shouldBeConfident_whenConfidenceIsAboveThreshold() {
        IntentClassifier.IntentClassificationResult result =
            new IntentClassifier.IntentClassificationResult(Intent.HELP, 0.85);

        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void intentClassificationResult_shouldNotBeConfident_whenConfidenceIsBelowThreshold() {
        IntentClassifier.IntentClassificationResult result =
            new IntentClassifier.IntentClassificationResult(Intent.UNKNOWN, 0.5);

        assertThat(result.isConfident()).isFalse();
    }

    @Test
    void intentClassificationResult_shouldThrowException_whenIntentIsNull() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new IntentClassifier.IntentClassificationResult(null, 0.8)
        );
    }

    @Test
    void intentClassificationResult_shouldThrowException_whenConfidenceIsNegative() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new IntentClassifier.IntentClassificationResult(Intent.HELP, -0.1)
        );
    }

    @Test
    void intentClassificationResult_shouldThrowException_whenConfidenceIsGreaterThanOne() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new IntentClassifier.IntentClassificationResult(Intent.HELP, 1.1)
        );
    }
}
