package com.zacknetic.zoomintegration.auth.service;

import com.zacknetic.zoomintegration.auth.model.ZoomUserToken;
import com.zacknetic.zoomintegration.auth.repository.ZoomUserTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing Zoom OAuth token storage and retrieval.
 *
 * Fellow Standards:
 * - Production Mindset: Transactional operations, proper error handling
 * - Security First: Token lifecycle management, expiry checking
 * - Fail Fast: Explicit validation and error conditions
 * - Explicit > Implicit: Clear method names and behavior
 *
 * This service handles the persistence layer for per-user Zoom OAuth tokens.
 */
@Service
public class ZoomTokenStorageService {

    private static final Logger log = LoggerFactory.getLogger(ZoomTokenStorageService.class);

    private final ZoomUserTokenRepository tokenRepository;

    public ZoomTokenStorageService(ZoomUserTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Store or update a user's Zoom OAuth token.
     *
     * Production: Upsert operation - creates new or updates existing
     * Security: Tokens should be encrypted before storage (TODO: add encryption layer)
     *
     * @param zoomUserId User's Zoom ID
     * @param zoomEmail User's email
     * @param accessToken OAuth access token
     * @param refreshToken OAuth refresh token (may be null)
     * @param expiresInSeconds Token expiration time in seconds
     * @param scopes Granted OAuth scopes
     * @return Saved token entity
     */
    @Transactional
    public ZoomUserToken saveToken(
        String zoomUserId,
        String zoomEmail,
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String scopes
    ) {
        if (zoomUserId == null || zoomUserId.isBlank()) {
            throw new IllegalArgumentException("Zoom user ID is required");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is required");
        }

        log.info("Saving Zoom token for user: {}", zoomUserId);

        // Find existing token or create new
        ZoomUserToken token = tokenRepository.findByZoomUserId(zoomUserId)
            .orElse(ZoomUserToken.builder()
                .zoomUserId(zoomUserId)
                .zoomEmail(zoomEmail)
                .build());

        // Update token information
        token.setZoomEmail(zoomEmail); // Update email in case it changed
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setTokenExpiry(Instant.now().plusSeconds(expiresInSeconds));
        token.setScopes(scopes);
        token.updateLastUsed();

        ZoomUserToken saved = tokenRepository.save(token);
        log.info("Token saved successfully for user: {} (expires: {})", zoomUserId, saved.getTokenExpiry());

        return saved;
    }

    /**
     * Get a user's token by Zoom user ID.
     *
     * Fail Fast: Returns Optional.empty() if not found
     *
     * @param zoomUserId User's Zoom ID
     * @return Optional containing token if found
     */
    public Optional<ZoomUserToken> getTokenByUserId(String zoomUserId) {
        if (zoomUserId == null || zoomUserId.isBlank()) {
            log.warn("Attempted to get token with null/blank user ID");
            return Optional.empty();
        }

        return tokenRepository.findByZoomUserId(zoomUserId);
    }

    /**
     * Get a user's token by email address.
     *
     * @param zoomEmail User's email
     * @return Optional containing token if found
     */
    public Optional<ZoomUserToken> getTokenByEmail(String zoomEmail) {
        if (zoomEmail == null || zoomEmail.isBlank()) {
            log.warn("Attempted to get token with null/blank email");
            return Optional.empty();
        }

        return tokenRepository.findByZoomEmail(zoomEmail);
    }

    /**
     * Get a valid (non-expired) access token for a user.
     *
     * Production: Checks token expiry before returning
     * Fail Fast: Returns empty if token expired or doesn't exist
     *
     * @param zoomUserId User's Zoom ID
     * @return Optional containing valid access token string
     */
    public Optional<String> getValidAccessToken(String zoomUserId) {
        Optional<ZoomUserToken> tokenOpt = getTokenByUserId(zoomUserId);

        if (tokenOpt.isEmpty()) {
            log.debug("No token found for user: {}", zoomUserId);
            return Optional.empty();
        }

        ZoomUserToken token = tokenOpt.get();

        if (token.isTokenExpired()) {
            log.warn("Token expired for user: {} (expired: {})", zoomUserId, token.getTokenExpiry());
            return Optional.empty();
        }

        // Update last used timestamp
        updateLastUsed(zoomUserId);

        return Optional.of(token.getAccessToken());
    }

    /**
     * Check if a user has a valid token stored.
     *
     * @param zoomUserId User's Zoom ID
     * @return true if valid token exists, false otherwise
     */
    public boolean hasValidToken(String zoomUserId) {
        return getValidAccessToken(zoomUserId).isPresent();
    }

    /**
     * Update the last used timestamp for a user's token.
     *
     * Production: Tracks user activity for analytics and cleanup
     *
     * @param zoomUserId User's Zoom ID
     */
    @Transactional
    public void updateLastUsed(String zoomUserId) {
        tokenRepository.findByZoomUserId(zoomUserId).ifPresent(token -> {
            token.updateLastUsed();
            tokenRepository.save(token);
        });
    }

    /**
     * Delete a user's token (logout).
     *
     * Production: Secure logout operation
     * Security: Removes all stored credentials
     *
     * @param zoomUserId User's Zoom ID
     */
    @Transactional
    public void deleteToken(String zoomUserId) {
        if (zoomUserId == null || zoomUserId.isBlank()) {
            log.warn("Attempted to delete token with null/blank user ID");
            return;
        }

        log.info("Deleting token for user: {}", zoomUserId);
        tokenRepository.deleteByZoomUserId(zoomUserId);
    }

    /**
     * Clean up tokens that haven't been used in a long time.
     *
     * Production: Maintenance operation for inactive users
     * Security: Reduces attack surface by removing unused credentials
     *
     * @param inactiveDays Number of days of inactivity before deletion
     * @return Number of tokens deleted
     */
    @Transactional
    public int cleanupInactiveTokens(int inactiveDays) {
        Instant threshold = Instant.now().minusSeconds(inactiveDays * 24L * 60 * 60);
        var inactiveTokens = tokenRepository.findByLastUsedAtBefore(threshold);

        log.info("Cleaning up {} inactive tokens (not used since: {})",
            inactiveTokens.size(), threshold);

        tokenRepository.deleteAll(inactiveTokens);
        return inactiveTokens.size();
    }

    /**
     * Get tokens that are expired or expiring soon.
     *
     * Production: Useful for proactive token refresh
     *
     * @param bufferSeconds Number of seconds before expiry to consider "expiring soon"
     * @return Number of tokens needing refresh
     */
    public int getExpiringTokenCount(long bufferSeconds) {
        Instant threshold = Instant.now().plusSeconds(bufferSeconds);
        return tokenRepository.findByTokenExpiryBefore(threshold).size();
    }
}
