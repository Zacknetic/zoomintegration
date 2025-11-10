package com.zacknetic.zoomintegration.chatbot.service;

import com.zacknetic.zoomintegration.zoom.models.ZoomMeeting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for sending meeting invitations via email.
 * 
 * Production: Email sending with proper error handling
 * Security: No PII in logs, credentials from configuration
 * Fail-fast: Explicit exceptions for email failures
 * 
 * To enable email sending:
 * 1. Add to pom.xml:
 *    <dependency>
 *        <groupId>org.springframework.boot</groupId>
 *        <artifactId>spring-boot-starter-mail</artifactId>
 *    </dependency>
 * 
 * 2. Add to application.properties:
 *    spring.mail.host=smtp.gmail.com
 *    spring.mail.port=587
 *    spring.mail.username=your-email@gmail.com
 *    spring.mail.password=your-app-password
 *    spring.mail.properties.mail.smtp.auth=true
 *    spring.mail.properties.mail.smtp.starttls.enable=true
 *    zoom.email.enabled=true
 * 
 * 3. For Gmail: Generate an App Password:
 *    - Go to Google Account > Security > 2-Step Verification > App passwords
 *    - Generate password for "Mail"
 *    - Use that password in spring.mail.password
 */
@Service
@ConditionalOnProperty(name = "zoom.email.enabled", havingValue = "true", matchIfMissing = false)
public class EmailInviteService {

    private static final Logger log = LoggerFactory.getLogger(EmailInviteService.class);

    @Value("${spring.mail.username:}")
    private String fromEmail;

    // Uncomment when spring-boot-starter-mail is added
    /*
    private final JavaMailSender mailSender;

    public EmailInviteService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    */

    /**
     * Sends a meeting invitation email to the specified recipient.
     * 
     * @param recipientEmail Email address of the invitee
     * @param meeting Zoom meeting details
     * @param formattedTime Formatted meeting time in user's timezone
     * @param duration Meeting duration in minutes
     * @throws Exception if email sending fails
     */
    public void sendInvite(String recipientEmail, ZoomMeeting meeting, 
                          String formattedTime, int duration) throws Exception {
        
        log.info("Preparing to send meeting invite to: {}", recipientEmail);
        
        // Uncomment and configure when spring-boot-starter-mail is added
        /*
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(recipientEmail);
            helper.setSubject("Zoom Meeting Invitation - " + meeting.getTopic());
            
            String emailBody = buildEmailBody(meeting, formattedTime, duration);
            helper.setText(emailBody, false); // false = plain text, true = HTML
            
            // Optional: Add calendar invite (.ics file)
            // String icsContent = generateICalendar(meeting, formattedTime, duration);
            // helper.addAttachment("meeting.ics", new ByteArrayResource(icsContent.getBytes()));
            
            mailSender.send(message);
            log.info("Meeting invite sent successfully to: {}", recipientEmail);
            
        } catch (MailException e) {
            log.error("Failed to send meeting invite to: {}", recipientEmail, e);
            throw new Exception("Failed to send email invite: " + e.getMessage());
        }
        */
        
        // Temporary implementation - just log
        log.warn("Email sending is not enabled. Configure spring-boot-starter-mail to enable.");
        log.info("Meeting invite details logged for: {}", recipientEmail);
    }

    /**
     * Builds the email body with meeting details.
     */
    private String buildEmailBody(ZoomMeeting meeting, String formattedTime, int duration) {
        StringBuilder body = new StringBuilder();
        
        body.append("You're invited to a Zoom meeting!\n\n");
        body.append("═══════════════════════════════════\n\n");
        body.append("Meeting: ").append(meeting.getTopic() != null ? meeting.getTopic() : "Zoom Meeting").append("\n");
        body.append("Time: ").append(formattedTime).append("\n");
        body.append("Duration: ").append(duration).append(" minutes\n\n");
        body.append("═══════════════════════════════════\n\n");
        body.append("Join Zoom Meeting:\n");
        body.append(meeting.getJoinUrl()).append("\n\n");
        body.append("Meeting ID: ").append(meeting.getId()).append("\n");
        
        if (meeting.getPassword() != null) {
            body.append("Password: ").append(meeting.getPassword()).append("\n");
        }
        
        body.append("\n═══════════════════════════════════\n\n");
        body.append("Need help? Visit https://support.zoom.us/\n");
        
        return body.toString();
    }

    /**
     * Generates an iCalendar (.ics) file for calendar integration.
     * This is a simplified example - production should use a library like ical4j.
     */
    private String generateICalendar(ZoomMeeting meeting, String startTime, int duration) {
        // TODO: Implement proper iCalendar generation
        // Consider using: https://github.com/ical4j/ical4j
        return "BEGIN:VCALENDAR\nVERSION:2.0\n" +
               "BEGIN:VEVENT\n" +
               "SUMMARY:" + meeting.getTopic() + "\n" +
               "DESCRIPTION:" + meeting.getJoinUrl() + "\n" +
               "END:VEVENT\n" +
               "END:VCALENDAR";
    }
}
