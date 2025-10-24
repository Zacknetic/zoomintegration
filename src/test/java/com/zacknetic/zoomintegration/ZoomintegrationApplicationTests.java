package com.zacknetic.zoomintegration;

import com.zacknetic.zoomintegration.config.ZoomConfig;
import com.zacknetic.zoomintegration.security.redaction.PIIRedactionService;
import com.zacknetic.zoomintegration.zoom.auth.ZoomOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying Spring Boot application context
 * and critical bean initialization
 */
@SpringBootTest
class ZoomintegrationApplicationTests {

	@Autowired
	private ZoomOAuthService oauthService;

	@Autowired
	private PIIRedactionService redactionService;

	@Autowired
	private ZoomConfig zoomConfig;

	/**
	 * Verify Spring context loads and critical beans are initialized
	 */
	@Test
	void contextLoads() {
		assertThat(oauthService).isNotNull();
		assertThat(redactionService).isNotNull();
		assertThat(zoomConfig).isNotNull();
	}

	/**
	 * Verify ZoomConfig loads configuration properly
	 */
	@Test
	void zoomConfigIsProperlyConfigured() {
		assertThat(zoomConfig.getApi()).isNotNull();
		assertThat(zoomConfig.getOauth()).isNotNull();
		assertThat(zoomConfig.getApi().getBaseUrl()).isNotBlank();
		assertThat(zoomConfig.getOauth().getTokenUrl()).isNotBlank();
	}

	/**
	 * Verify PIIRedactionService works correctly
	 */
	@Test
	void piiRedactionServiceWorksCorrectly() {
		String input = "User email: test@example.com, phone: 555-123-4567";
		String redacted = redactionService.redactForLogging(input);

		assertThat(redacted).doesNotContain("test@example.com");
		assertThat(redacted).doesNotContain("555-123-4567");
		assertThat(redacted).contains("[REDACTED]");
	}

	/**
	 * Verify PIIRedactionService can detect PII
	 */
	@Test
	void piiDetectionWorks() {
		assertThat(redactionService.containsPII("test@example.com")).isTrue();
		assertThat(redactionService.containsPII("Normal log message")).isFalse();
	}

	/**
	 * Verify token masking works correctly
	 */
	@Test
	void tokenMaskingWorksCorrectly() {
		String longToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0";
		String masked = redactionService.maskToken(longToken);

		assertThat(masked).contains("...");
		assertThat(masked.length()).isLessThan(longToken.length());

		String shortToken = "short";
		String maskedShort = redactionService.maskToken(shortToken);
		assertThat(maskedShort).isEqualTo("[REDACTED]");
	}
}
