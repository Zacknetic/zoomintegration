package com.zacknetic.zoomintegration.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for API documentation
 *
 * Provides interactive API documentation at /swagger-ui.html
 * API spec available at /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    @Value("${spring.application.name:zoom-integration}")
    private String applicationName;

    @Bean
    public OpenAPI zoomIntegrationOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Zoom Integration API")
                .description("""
                    Spring Boot integration with Zoom API featuring:
                    - Server-to-Server OAuth authentication
                    - User API access
                    - PII redaction for secure logging
                    - Production-ready monitoring and health checks

                    Built following Fellow engineering standards:
                    - Security-first design
                    - 12-Factor compliant
                    - Comprehensive error handling
                    - Thread-safe token caching
                    """)
                .version(appVersion)
                .contact(new Contact()
                    .name("API Support")
                    .email("support@example.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Development server"),
                new Server()
                    .url("https://api.example.com")
                    .description("Production server")
            ));
    }
}
