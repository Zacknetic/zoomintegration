package com.zacknetic.zoomintegration.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for JWT-based authentication.
 *
 * Fellow Standards:
 * - Security First: All endpoints secured by default, explicit public routes
 * - Production Mindset: Stateless sessions for scalability
 * - Fail Fast: Unauthorized requests immediately rejected
 * - Explicit > Implicit: Clear security rules and exception handling
 *
 * This configuration:
 * 1. Enables JWT authentication via custom filter
 * 2. Disables CSRF (not needed for stateless JWT)
 * 3. Configures public vs authenticated endpoints
 * 4. Sets stateless session management
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Configure security filter chain.
     *
     * Fellow: Security First - default deny, explicit allow
     * Production: Stateless sessions for horizontal scalability
     *
     * @param http HttpSecurity configuration
     * @return Configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (not needed for stateless JWT authentication)
            .csrf(AbstractHttpConfigurer::disable)

            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - OAuth flow and health checks
                .requestMatchers(
                    "/api/auth/login",        // OAuth initiation
                    "/api/auth/callback",     // OAuth callback (redirects to HTML)
                    "/api/auth/token",        // Token exchange (called by frontend)
                    "/api/test/**",           // Test endpoints (remove in production)
                    "/actuator/health",       // Health check
                    "/actuator/info",         // Info endpoint
                    "/swagger-ui/**",         // Swagger UI
                    "/v3/api-docs/**",        // OpenAPI docs
                    "/login.html",            // Login page
                    "/auth-success.html",     // Auth success page
                    "/chatbot.html",          // Chatbot UI (will need auth via JS)
                    "/*.css",                 // Static CSS
                    "/*.js",                  // Static JS
                    "/error"                  // Error page
                ).permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Stateless session management (no server-side sessions)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Add JWT filter before Spring Security's authentication filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
