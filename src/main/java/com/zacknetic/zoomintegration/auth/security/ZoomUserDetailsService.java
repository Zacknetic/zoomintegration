package com.zacknetic.zoomintegration.auth.security;

import com.zacknetic.zoomintegration.auth.model.ZoomUserToken;
import com.zacknetic.zoomintegration.auth.repository.ZoomUserTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Custom UserDetailsService for loading Zoom user information.
 *
 * Fellow Standards:
 * - Security First: Validates user exists before granting access
 * - Fail Fast: Throws UsernameNotFoundException immediately
 * - Production Mindset: Proper error handling and logging
 * - Explicit > Implicit: Clear user loading behavior
 *
 * This service is used by Spring Security to load user details during authentication.
 */
@Service
public class ZoomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(ZoomUserDetailsService.class);

    private final ZoomUserTokenRepository tokenRepository;

    public ZoomUserDetailsService(ZoomUserTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Load user by Zoom user ID.
     *
     * Fellow: Fail Fast - throws exception if user not found
     * Security: Only allows authenticated Zoom users with valid tokens
     *
     * @param zoomUserId The Zoom user ID (username in Spring Security context)
     * @return UserDetails for Spring Security
     * @throws UsernameNotFoundException if user not found or token invalid
     */
    @Override
    public UserDetails loadUserByUsername(String zoomUserId) throws UsernameNotFoundException {
        if (zoomUserId == null || zoomUserId.isBlank()) {
            log.warn("Attempted to load user with null/blank user ID");
            throw new UsernameNotFoundException("User ID cannot be null or empty");
        }

        log.debug("Loading user details for Zoom user ID: {}", zoomUserId);

        // Fetch user token from database
        ZoomUserToken token = tokenRepository.findByZoomUserId(zoomUserId)
            .orElseThrow(() -> {
                log.warn("User not found: {}", zoomUserId);
                return new UsernameNotFoundException("User not found: " + zoomUserId);
            });

        // Check if token is expired
        if (token.isTokenExpired()) {
            log.warn("User token expired: {}", zoomUserId);
            throw new UsernameNotFoundException("User token expired: " + zoomUserId);
        }

        // Build Spring Security UserDetails
        // Note: We don't store passwords since we use OAuth
        // The empty password is fine because we authenticate via JWT tokens
        return User.builder()
            .username(token.getZoomUserId())
            .password("") // No password - OAuth authenticated
            .authorities(new ArrayList<>()) // Could map Zoom scopes to authorities
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(token.isTokenExpired())
            .disabled(false)
            .build();
    }

    /**
     * Get the authenticated user's Zoom email.
     * Helper method for controllers to access user information.
     *
     * @param zoomUserId Zoom user ID
     * @return User's email address or null if not found
     */
    public String getUserEmail(String zoomUserId) {
        return tokenRepository.findByZoomUserId(zoomUserId)
            .map(ZoomUserToken::getZoomEmail)
            .orElse(null);
    }

    /**
     * Check if a user has a specific OAuth scope.
     * Useful for fine-grained authorization.
     *
     * @param zoomUserId Zoom user ID
     * @param scope Required scope
     * @return true if user has the scope
     */
    public boolean hasScope(String zoomUserId, String scope) {
        return tokenRepository.findByZoomUserId(zoomUserId)
            .map(token -> {
                String scopes = token.getScopes();
                return scopes != null && scopes.contains(scope);
            })
            .orElse(false);
    }
}
