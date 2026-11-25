package com.example.ecommerce.service;

import com.example.ecommerce.model.ZohoUserToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ZohoTokenService {

    private static final Logger log = LoggerFactory.getLogger(ZohoTokenService.class);

    private final Map<String, ZohoUserToken> userTokens = new ConcurrentHashMap<>();
    private final RestClient restClient = RestClient.create();

    @Value("${zoho.accounts.base-url:https://accounts.zoho.in}")
    private String accountsBaseUrl;

    @Value("${zoho.client-id}")
    private String clientId;

    @Value("${zoho.client-secret}")
    private String clientSecret;

    public void saveToken(
            String userId,
            String email,
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            String scope) {

        if (userId == null || userId.isBlank()) {
            return;
        }

        ZohoUserToken existing = userTokens.get(userId);
        String finalRefreshToken = refreshToken;
        if ((finalRefreshToken == null || finalRefreshToken.isBlank()) && existing != null) {
            finalRefreshToken = existing.getRefreshToken();
        }

        ZohoUserToken token = ZohoUserToken.builder()
                .userId(userId)
                .email(email != null ? email : (existing != null ? existing.getEmail() : userId))
                .accessToken(accessToken)
                .refreshToken(finalRefreshToken)
                .expiresAt(expiresAt)
                .scope(scope)
                .tokenType("Bearer")
                .build();

        userTokens.put(userId, token);
        if (email != null && !email.equalsIgnoreCase(userId)) {
            userTokens.put(email, token);
        }

        log.info("Securely saved Zoho tokens for user {} (has refresh token: {})",
                userId, finalRefreshToken != null && !finalRefreshToken.isBlank());
    }

    public Optional<ZohoUserToken> getToken(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userTokens.get(userId));
    }

    public boolean hasToken(String userId) {
        return userId != null && userTokens.containsKey(userId);
    }

    public synchronized String getValidAccessToken(String userId) {
        Optional<ZohoUserToken> opt = getToken(userId);
        if (opt.isEmpty()) {
            log.warn("No Zoho token found for user: {}", userId);
            return null;
        }

        ZohoUserToken token = opt.get();

        if (!token.isExpired() && token.getAccessToken() != null && !token.getAccessToken().isBlank()) {
            return token.getAccessToken();
        }

        log.info("Zoho access token for user {} is expired or expiring; refreshing token...", userId);
        return refreshAccessToken(token);
    }

    public synchronized String refreshAccessToken(ZohoUserToken token) {
        if (token == null || token.getRefreshToken() == null || token.getRefreshToken().isBlank()) {
            log.warn("Cannot refresh access token: missing refresh token for user {}",
                    token != null ? token.getUserId() : "unknown");
            return null;
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            formData.add("refresh_token", token.getRefreshToken());
            formData.add("grant_type", "refresh_token");

            String tokenUri = accountsBaseUrl.replaceAll("/+$", "") + "/oauth/v2/token";
            log.debug("Sending refresh token request to {}", tokenUri);

            Map<?, ?> response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                log.error("Failed to refresh Zoho access token: response did not contain access_token: {}", response);
                return null;
            }

            String newAccessToken = String.valueOf(response.get("access_token"));
            long expiresIn = 3600L;
            if (response.containsKey("expires_in")) {
                try {
                    expiresIn = Long.parseLong(String.valueOf(response.get("expires_in")));
                } catch (NumberFormatException ignored) {
                }
            }

            Instant newExpiresAt = Instant.now().plusSeconds(expiresIn);
            token.setAccessToken(newAccessToken);
            token.setExpiresAt(newExpiresAt);

            log.info("Successfully refreshed Zoho access token for user {}, expires in {} seconds",
                    token.getUserId(), expiresIn);
            return newAccessToken;

        } catch (Exception ex) {
            log.error("Exception while refreshing Zoho access token for user {}: {}",
                    token.getUserId(), ex.getMessage(), ex);
            return null;
        }
    }

    public void removeToken(String userId) {
        if (userId != null) {
            ZohoUserToken removed = userTokens.remove(userId);
            if (removed != null && removed.getEmail() != null) {
                userTokens.remove(removed.getEmail());
            }
            log.info("Removed stored Zoho tokens for user: {}", userId);
        }
    }
}
