package com.zacknetic.zoomintegration.auth.security;

import com.zacknetic.zoomintegration.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for extracting and validating JWT tokens from requests.
 *
 * Fellow Standards:
 * - Security First: Token validation before granting access
 * - Fail Fast: Invalid tokens immediately rejected
 * - Production Mindset: Proper error handling and logging
 * - Explicit > Implicit: Clear authentication flow
 *
 * This filter intercepts every HTTP request and checks for a valid JWT token
 * in the Authorization header. If valid, it sets the Spring Security context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ZoomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, ZoomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Extract JWT token from Authorization header
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // Skip authentication if no token present
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extract token (remove "Bearer " prefix)
            final String jwt = authHeader.substring(BEARER_PREFIX.length());

            // Extract user ID from token
            final String zoomUserId = jwtService.extractZoomUserId(jwt);

            // If user ID present and not already authenticated
            if (zoomUserId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Validate token type (must be access token)
                if (!jwtService.validateTokenType(jwt, "access")) {
                    log.warn("Invalid token type for user: {}", zoomUserId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Load user details
                UserDetails userDetails = userDetailsService.loadUserByUsername(zoomUserId);

                // Validate token
                if (jwtService.validateToken(jwt)) {
                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );

                    // Set additional details from request
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("Authenticated user: {}", zoomUserId);
                } else {
                    log.warn("Invalid JWT token for user: {}", zoomUserId);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
