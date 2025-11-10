package com.zacknetic.zoomintegration.auth.repository;

import com.zacknetic.zoomintegration.auth.model.ZoomUserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Zoom user OAuth tokens.
 *
 * Fellow Standards:
 * - Production Mindset: Efficient queries with proper indexing
 * - Explicit > Implicit: Clear method names indicating intent
 * - Fail Fast: Return Optional for nullable results
 */
@Repository
public interface ZoomUserTokenRepository extends JpaRepository<ZoomUserToken, Long> {

    /**
     * Find a user's token by their Zoom user ID.
     *
     * @param zoomUserId Zoom user ID
     * @return Optional containing the token if found
     */
    Optional<ZoomUserToken> findByZoomUserId(String zoomUserId);

    /**
     * Find a user's token by their Zoom email address.
     *
     * @param zoomEmail Zoom email address
     * @return Optional containing the token if found
     */
    Optional<ZoomUserToken> findByZoomEmail(String zoomEmail);

    /**
     * Check if a token exists for a given Zoom user ID.
     *
     * @param zoomUserId Zoom user ID
     * @return true if token exists, false otherwise
     */
    boolean existsByZoomUserId(String zoomUserId);

    /**
     * Find all tokens that haven't been used since a certain time.
     * Useful for cleanup of inactive users.
     *
     * @param lastUsedBefore Timestamp threshold
     * @return List of inactive tokens
     */
    List<ZoomUserToken> findByLastUsedAtBefore(Instant lastUsedBefore);

    /**
     * Find all tokens expiring before a certain time.
     * Useful for proactive token refresh.
     *
     * @param expiryBefore Timestamp threshold
     * @return List of expiring tokens
     */
    List<ZoomUserToken> findByTokenExpiryBefore(Instant expiryBefore);

    /**
     * Delete token by Zoom user ID.
     * Used when user revokes access or logs out.
     *
     * @param zoomUserId Zoom user ID
     */
    void deleteByZoomUserId(String zoomUserId);
}
