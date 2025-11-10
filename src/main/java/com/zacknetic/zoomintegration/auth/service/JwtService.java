package com.zacknetic.zoomintegration.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating and validating JWT tokens for application authentication.
 *
 * Fellow Standards:
 * - Production Mindset: Secure token generation with proper expiration
 * - Security First: HMAC SHA-512 signing, secret key management
 * - Fail Fast: Explicit exception handling for invalid tokens
 * - Explicit > Implicit: Clear method signatures and documentation
 *
 * This handles our application's internal authentication tokens (NOT Zoom OAuth tokens).
 * After a user authenticates with Zoom, we issue them a JWT for subsequent API calls.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey secretKey;
    private final long jwtExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-ms}") long jwtExpirationMs,
        @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        // Security: Use HMAC-SHA512 with minimum 256-bit key
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;

        log.info("JWT service initialized. Token expiration: {}ms, Refresh expiration: {}ms",
            jwtExpirationMs, refreshExpirationMs);
    }

    /**
     * Generate an access token for a user.
     *
     * Production: Short-lived token for API access (24 hours default)
     * Security: Contains minimal user info to reduce exposure
     *
     * @param zoomUserId User's Zoom user ID
     * @param zoomEmail User's email address
     * @return JWT access token
     */
    public String generateAccessToken(String zoomUserId, String zoomEmail) {
        if (zoomUserId == null || zoomUserId.isBlank()) {
            throw new IllegalArgumentException("Zoom user ID is required");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", zoomEmail);
        claims.put("type", "access");

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtExpirationMs);

        String token = Jwts.builder()
            .claims(claims)
            .subject(zoomUserId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact();

        log.debug("Generated access token for user: {} (expires: {})", zoomUserId, expiry);
        return token;
    }

    /**
     * Generate a refresh token for a user.
     *
     * Production: Long-lived token for obtaining new access tokens (7 days default)
     * Security: Contains minimal info, single-use recommended (implement token rotation)
     *
     * @param zoomUserId User's Zoom user ID
     * @return JWT refresh token
     */
    public String generateRefreshToken(String zoomUserId) {
        if (zoomUserId == null || zoomUserId.isBlank()) {
            throw new IllegalArgumentException("Zoom user ID is required");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshExpirationMs);

        String token = Jwts.builder()
            .claims(claims)
            .subject(zoomUserId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact();

        log.debug("Generated refresh token for user: {} (expires: {})", zoomUserId, expiry);
        return token;
    }

    /**
     * Extract Zoom user ID from JWT token.
     *
     * Fail Fast: Throws exceptions for invalid/expired tokens
     *
     * @param token JWT token
     * @return Zoom user ID (subject)
     * @throws JwtException if token is invalid or expired
     */
    public String extractZoomUserId(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extract email from JWT token.
     *
     * @param token JWT token
     * @return Email address or null if not present
     * @throws JwtException if token is invalid or expired
     */
    public String extractEmail(String token) {
        Claims claims = extractClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Extract token type (access or refresh).
     *
     * @param token JWT token
     * @return Token type
     * @throws JwtException if token is invalid or expired
     */
    public String extractTokenType(String token) {
        Claims claims = extractClaims(token);
        return claims.get("type", String.class);
    }

    /**
     * Validate a JWT token.
     *
     * Production: Comprehensive validation including signature, expiration
     * Fail Fast: Returns false for any validation failure
     *
     * @param token JWT token
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Validate a token is of the expected type.
     *
     * @param token JWT token
     * @param expectedType Expected token type ("access" or "refresh")
     * @return true if valid and correct type
     */
    public boolean validateTokenType(String token, String expectedType) {
        if (!validateToken(token)) {
            return false;
        }

        try {
            String tokenType = extractTokenType(token);
            return expectedType.equals(tokenType);
        } catch (Exception e) {
            log.warn("Failed to validate token type: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a token is expired.
     *
     * @param token JWT token
     * @return true if expired, false if valid or unparseable
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            log.warn("Failed to check token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Extract all claims from a JWT token.
     *
     * Production: Central method for token parsing with proper validation
     * Security: Verifies signature using secret key
     *
     * @param token JWT token
     * @return Claims object
     * @throws JwtException if token is invalid
     */
    private Claims extractClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
