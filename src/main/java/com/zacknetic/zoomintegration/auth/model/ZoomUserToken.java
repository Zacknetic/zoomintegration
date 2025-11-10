package com.zacknetic.zoomintegration.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for storing per-user Zoom OAuth tokens.
 *
 * Fellow Standards:
 * - Production Mindset: Stores encrypted tokens with expiry tracking
 * - Security First: Tokens should be encrypted at rest (TODO: add encryption)
 * - Explicit > Implicit: Clear field names and purpose
 * - Fail Fast: Token expiry explicitly tracked and validated
 *
 * Each user who authenticates with Zoom gets their own tokens stored here,
 * allowing the application to act on their behalf for Zoom API calls.
 */
@Entity
@Table(name = "zoom_user_tokens", indexes = {
    @Index(name = "idx_zoom_user_id", columnList = "zoom_user_id"),
    @Index(name = "idx_zoom_email", columnList = "zoom_email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomUserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Zoom user ID (unique identifier from Zoom)
     * Example: "oGNm9EkOTZCppzgddrmP_g"
     */
    @Column(name = "zoom_user_id", nullable = false, unique = true, length = 100)
    private String zoomUserId;

    /**
     * User's email address from Zoom
     * Used for user identification and logging
     */
    @Column(name = "zoom_email", nullable = false, length = 254)
    private String zoomEmail;

    /**
     * OAuth access token for Zoom API calls
     * Security: Should be encrypted at rest in production
     */
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    /**
     * OAuth refresh token for obtaining new access tokens
     * Security: Should be encrypted at rest in production
     * Note: Not all Zoom OAuth flows provide refresh tokens
     */
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    /**
     * When the access token expires
     * Used to determine when token refresh is needed
     */
    @Column(name = "token_expiry", nullable = false)
    private Instant tokenExpiry;

    /**
     * Granted scopes from Zoom OAuth
     * Example: "meeting:write meeting:read user:read"
     */
    @Column(name = "scopes", length = 1000)
    private String scopes;

    /**
     * When this record was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When this record was last updated
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * When the user last used the application
     * Useful for cleanup of inactive users
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        lastUsedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if the access token is expired or about to expire.
     * Considers token expired if it expires within the next 5 minutes.
     *
     * Fellow: Fail Fast - explicitly check expiry with buffer
     */
    public boolean isTokenExpired() {
        if (tokenExpiry == null) {
            return true;
        }
        // Consider expired if less than 5 minutes remaining
        Instant now = Instant.now();
        Instant expiryWithBuffer = tokenExpiry.minusSeconds(300);
        return now.isAfter(expiryWithBuffer);
    }

    /**
     * Update the last used timestamp.
     * Call this when the user makes an API request.
     */
    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
    }
}
