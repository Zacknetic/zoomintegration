package com.zacknetic.zoomintegration.auth.service;

import com.zacknetic.zoomintegration.auth.model.ZoomUserToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

/**
 * Service for accessing Zoom API on behalf of authenticated users.
 *
 * Fellow Standards:
 * - Security First: Per-user token management, validates token expiry
 * - Fail Fast: Throws exceptions for invalid/expired tokens
 * - Production Mindset: Token refresh handling (TODO), proper error messages
 * - Explicit > Implicit: Clear separation from app-level OAuth
 *
 * This service provides user-specific Zoom API access tokens.
 * Unlike ZoomOAuthService (app-level), this uses individual user OAuth tokens.
 */
@Service
public class UserZoomApiService {

    private static final Logger log = LoggerFactory.getLogger(UserZoomApiService.class);

    private final ZoomTokenStorageService tokenStorageService;

    public UserZoomApiService(ZoomTokenStorageService tokenStorageService) {
        this.tokenStorageService = tokenStorageService;
    }

    /**
     * Get a valid access token for a specific user.
     *
     * Production: Checks token validity, handles expiry
     * Security: Returns user's own token, not shared app token
     * Fail Fast: Throws exception if token missing or expired
     *
     * @param zoomUserId User's Zoom ID
     * @return Valid access token
     * @throws IOException if token missing, expired, or refresh fails
     */
    public String getAccessTokenForUser(String zoomUserId) throws IOException {
        if (zoomUserId == null || zoomUserId.isBlank()) {
            throw new IllegalArgumentException("Zoom user ID is required");
        }

        log.debug("Getting access token for user: {}", zoomUserId);

        // Try to get valid token from storage
        Optional<String> tokenOpt = tokenStorageService.getValidAccessToken(zoomUserId);

        if (tokenOpt.isPresent()) {
            return tokenOpt.get();
        }

        // Token missing or expired - need to refresh
        // TODO: Implement token refresh flow
        log.error("Token expired or missing for user: {}", zoomUserId);
        throw new IOException(
            "Zoom token expired or missing. User needs to re-authenticate at /api/auth/login"
        );
    }

    /**
     * Get the full user token object (includes email, expiry, etc).
     *
     * @param zoomUserId User's Zoom ID
     * @return User token entity
     * @throws IOException if token not found
     */
    public ZoomUserToken getUserToken(String zoomUserId) throws IOException {
        return tokenStorageService.getTokenByUserId(zoomUserId)
            .orElseThrow(() -> new IOException("User token not found: " + zoomUserId));
    }

    /**
     * Get user's Zoom email address.
     *
     * @param zoomUserId User's Zoom ID
     * @return User's email or null if not found
     */
    public String getUserEmail(String zoomUserId) {
        return tokenStorageService.getTokenByUserId(zoomUserId)
            .map(ZoomUserToken::getZoomEmail)
            .orElse(null);
    }

    /**
     * Check if a user has a valid token stored.
     *
     * @param zoomUserId User's Zoom ID
     * @return true if user has valid token
     */
    public boolean hasValidToken(String zoomUserId) {
        return tokenStorageService.hasValidToken(zoomUserId);
    }

    /**
     * Refresh an expired token.
     *
     * TODO: Implement token refresh using refresh_token
     * Production: Automatically refresh tokens when expired
     * Security: Store and use refresh tokens securely
     *
     * @param zoomUserId User's Zoom ID
     * @throws IOException if refresh fails
     */
    public void refreshToken(String zoomUserId) throws IOException {
        log.info("Token refresh not yet implemented for user: {}", zoomUserId);
        throw new UnsupportedOperationException(
            "Token refresh not yet implemented. User must re-authenticate at /api/auth/login"
        );
    }
}
