package com.zacknetic.zoomintegration.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zacknetic.zoomintegration.auth.service.JwtService;
import com.zacknetic.zoomintegration.auth.service.ZoomTokenStorageService;
import com.zacknetic.zoomintegration.config.ZoomConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Controller for Zoom OAuth 2.0 user authentication flow.
 *
 * Fellow Standards:
 * - Security First: Proper OAuth 2.0 implementation, state parameter for CSRF
 * - Production Mindset: Comprehensive error handling and logging
 * - Fail Fast: Invalid OAuth responses immediately rejected
 * - Explicit > Implicit: Clear flow documentation and error messages
 *
 * OAuth Flow:
 * 1. User clicks "Login with Zoom"
 * 2. GET /api/auth/login - Redirects to Zoom authorization page
 * 3. User approves on Zoom
 * 4. Zoom redirects to GET /api/auth/callback with authorization code
 * 5. Backend exchanges code for access token
 * 6. Backend issues JWT token to user
 * 7. User uses JWT for subsequent API calls
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Zoom OAuth authentication endpoints")
public class ZoomOAuthController {

    private static final Logger log = LoggerFactory.getLogger(ZoomOAuthController.class);

    private final ZoomConfig zoomConfig;
    private final ZoomTokenStorageService tokenStorageService;
    private final JwtService jwtService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ZoomOAuthController(
        ZoomConfig zoomConfig,
        ZoomTokenStorageService tokenStorageService,
        JwtService jwtService,
        OkHttpClient httpClient,
        ObjectMapper objectMapper
    ) {
        this.zoomConfig = zoomConfig;
        this.tokenStorageService = tokenStorageService;
        this.jwtService = jwtService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Initiate Zoom OAuth login flow.
     *
     * Production: Redirects user to Zoom authorization page
     * Security: Should include state parameter for CSRF protection (TODO)
     *
     * @return Redirect URL to Zoom OAuth authorization page
     */
    @GetMapping("/login")
    @Operation(summary = "Login with Zoom", description = "Redirects to Zoom OAuth authorization page")
    public ResponseEntity<LoginResponse> login() {
        log.info("Initiating Zoom OAuth login flow");

        try {
            // Build authorization URL
            String authUrl = UriComponentsBuilder
                .fromHttpUrl(zoomConfig.getOauth().getUser().getAuthorizationUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", zoomConfig.getOauth().getUser().getClientId())
                .queryParam("redirect_uri", URLEncoder.encode(
                    zoomConfig.getOauth().getUser().getRedirectUri(),
                    StandardCharsets.UTF_8
                ))
                .build()
                .toUriString();

            log.debug("Authorization URL: {}", authUrl);

            return ResponseEntity.ok(LoginResponse.builder()
                .authorizationUrl(authUrl)
                .message("Redirect user to this URL to complete Zoom OAuth")
                .build());

        } catch (Exception e) {
            log.error("Failed to build authorization URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(LoginResponse.builder()
                    .error("Failed to initiate login")
                    .build());
        }
    }

    /**
     * Handle OAuth callback from Zoom.
     *
     * Production: Exchanges authorization code for access token
     * Security: Should validate state parameter for CSRF protection (TODO)
     * Fail Fast: Rejects invalid codes or failed token exchanges
     *
     * This endpoint handles both browser redirects (redirects to success page)
     * and API calls (returns JSON response).
     *
     * @param code Authorization code from Zoom
     * @param error Error if user denied access
     * @return Redirect to success page with callback data as query params (for browser)
     *         or JSON response (for API calls)
     */
    @GetMapping("/callback")
    @Operation(summary = "OAuth callback", description = "Handles OAuth callback from Zoom")
    public Object callback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String error
    ) {
        log.info("Received OAuth callback");

        // Redirect to HTML success page with code/error for browser-friendly UX
        // The HTML page will call /api/auth/token to exchange the code
        if (error != null) {
            return new org.springframework.web.servlet.view.RedirectView("/auth-success.html?error=" + error);
        }

        if (code != null && !code.isBlank()) {
            return new org.springframework.web.servlet.view.RedirectView("/auth-success.html?code=" + code);
        }

        // Fallback error
        return new org.springframework.web.servlet.view.RedirectView("/auth-success.html?error=missing_code");
    }

    /**
     * Exchange authorization code for JWT tokens (called by frontend).
     *
     * @param code Authorization code from Zoom
     * @return JWT tokens for application access
     */
    @GetMapping("/token")
    @Operation(summary = "Exchange code for token", description = "Exchange authorization code for JWT tokens")
    public ResponseEntity<CallbackResponse> exchangeToken(@RequestParam String code) {
        if (code == null || code.isBlank()) {
            log.warn("Token exchange missing authorization code");
            return ResponseEntity.badRequest()
                .body(CallbackResponse.builder()
                    .error("Missing authorization code")
                    .build());
        }

        log.info("Exchanging authorization code for access token");

        try {
            // Exchange authorization code for access token
            TokenExchangeResult tokenResult = exchangeCodeForToken(code);

            // Fetch user info from Zoom
            log.info("Fetching user info from Zoom");
            ZoomUserInfo userInfo = fetchZoomUserInfo(tokenResult.accessToken);

            // Store Zoom tokens
            log.info("Storing Zoom tokens for user: {}", userInfo.id);
            tokenStorageService.saveToken(
                userInfo.id,
                userInfo.email,
                tokenResult.accessToken,
                tokenResult.refreshToken,
                tokenResult.expiresIn,
                tokenResult.scope
            );

            // Generate JWT tokens for application auth
            String jwtAccessToken = jwtService.generateAccessToken(userInfo.id, userInfo.email);
            String jwtRefreshToken = jwtService.generateRefreshToken(userInfo.id);

            log.info("OAuth flow completed successfully for user: {}", userInfo.email);

            return ResponseEntity.ok(CallbackResponse.builder()
                .accessToken(jwtAccessToken)
                .refreshToken(jwtRefreshToken)
                .tokenType("Bearer")
                .email(userInfo.email)
                .zoomUserId(userInfo.id)
                .message("Login successful")
                .build());

        } catch (IOException e) {
            log.error("Failed to complete OAuth flow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CallbackResponse.builder()
                    .error("Failed to exchange authorization code: " + e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Unexpected error during OAuth callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CallbackResponse.builder()
                    .error("Authentication failed")
                    .build());
        }
    }

    /**
     * Get current authenticated user information.
     *
     * Production: Returns user details from stored token
     * Security: Only returns info for authenticated user
     *
     * @return User information
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get authenticated user information")
    public ResponseEntity<UserInfoResponse> getCurrentUser() {
        // Extract authenticated user from Spring Security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(UserInfoResponse.builder()
                    .error("Authentication required")
                    .build());
        }

        String zoomUserId = authentication.getName();

        try {
            // Get user token to retrieve email
            var token = tokenStorageService.getTokenByUserId(zoomUserId);

            if (token.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(UserInfoResponse.builder()
                        .error("User token not found")
                        .build());
            }

            return ResponseEntity.ok(UserInfoResponse.builder()
                .zoomUserId(token.get().getZoomUserId())
                .email(token.get().getZoomEmail())
                .scopes(token.get().getScopes())
                .tokenExpiry(token.get().getTokenExpiry())
                .build());

        } catch (Exception e) {
            log.error("Failed to get user info for: {}", zoomUserId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserInfoResponse.builder()
                    .error("Failed to retrieve user information")
                    .build());
        }
    }

    /**
     * Logout current user.
     *
     * Production: Removes stored tokens
     * Security: Invalidates user session
     *
     * @return Logout confirmation
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout current user and revoke tokens")
    public ResponseEntity<LogoutResponse> logout() {
        // Extract authenticated user from Spring Security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(LogoutResponse.builder()
                    .success(false)
                    .error("Authentication required")
                    .build());
        }

        String zoomUserId = authentication.getName();
        log.info("Logging out user: {}", zoomUserId);

        try {
            tokenStorageService.deleteToken(zoomUserId);

            return ResponseEntity.ok(LogoutResponse.builder()
                .success(true)
                .message("Logged out successfully")
                .build());

        } catch (Exception e) {
            log.error("Failed to logout user: {}", zoomUserId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(LogoutResponse.builder()
                    .success(false)
                    .error("Logout failed")
                    .build());
        }
    }

    /**
     * Exchange authorization code for access token.
     *
     * Production: Standard OAuth 2.0 token exchange
     * Security: Uses client credentials for authentication
     *
     * @param code Authorization code from Zoom
     * @return Token exchange result
     * @throws IOException if request fails
     */
    private TokenExchangeResult exchangeCodeForToken(String code) throws IOException {
        // Build Basic Auth header
        String credentials = zoomConfig.getOauth().getUser().getClientId() + ":" +
                           zoomConfig.getOauth().getUser().getClientSecret();
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        // Build request body
        okhttp3.RequestBody body = new FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", zoomConfig.getOauth().getUser().getRedirectUri())
            .build();

        // Make token request
        Request request = new Request.Builder()
            .url(zoomConfig.getOauth().getUser().getTokenUrl())
            .post(body)
            .addHeader("Authorization", "Basic " + encodedCredentials)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("Token exchange failed. Status: {}, Body: {}", response.code(), responseBody);
                throw new IOException("Token exchange failed: HTTP " + response.code());
            }

            // Parse response
            JsonNode json = objectMapper.readTree(responseBody);

            return TokenExchangeResult.builder()
                .accessToken(json.get("access_token").asText())
                .refreshToken(json.has("refresh_token") ? json.get("refresh_token").asText() : null)
                .expiresIn(json.get("expires_in").asLong())
                .scope(json.has("scope") ? json.get("scope").asText() : null)
                .build();
        }
    }

    /**
     * Fetch user information from Zoom API.
     *
     * Production: Gets user profile to store with token
     * Security: Uses access token for authentication
     *
     * @param accessToken Zoom access token
     * @return User information
     * @throws IOException if request fails
     */
    private ZoomUserInfo fetchZoomUserInfo(String accessToken) throws IOException {
        Request request = new Request.Builder()
            .url(zoomConfig.getOauth().getUser().getUserInfoUrl())
            .get()
            .addHeader("Authorization", "Bearer " + accessToken)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("Failed to fetch user info. Status: {}, Body: {}", response.code(), responseBody);
                throw new IOException("Failed to fetch user info: HTTP " + response.code());
            }

            // Parse response
            JsonNode json = objectMapper.readTree(responseBody);

            return ZoomUserInfo.builder()
                .id(json.get("id").asText())
                .email(json.get("email").asText())
                .firstName(json.has("first_name") ? json.get("first_name").asText() : null)
                .lastName(json.has("last_name") ? json.get("last_name").asText() : null)
                .build();
        }
    }

    // DTOs

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String authorizationUrl;
        private String message;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallbackResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private String email;
        private String zoomUserId;
        private String message;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogoutResponse {
        private boolean success;
        private String message;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfoResponse {
        private String zoomUserId;
        private String email;
        private String scopes;
        private java.time.Instant tokenExpiry;
        private String error;
    }

    @Data
    @Builder
    private static class TokenExchangeResult {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private String scope;
    }

    @Data
    @Builder
    private static class ZoomUserInfo {
        private String id;
        private String email;
        private String firstName;
        private String lastName;
    }
}
