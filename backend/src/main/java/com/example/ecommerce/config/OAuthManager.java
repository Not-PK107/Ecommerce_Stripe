
package com.example.ecommerce.config;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class OAuthManager {

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    @Value("${zoho.catalyst.client-id}")
    private String zohoClientId;

    @Value("${zoho.catalyst.client-secret}")
    private String zohoClientSecret;

    @Value("${zoho.catalyst.refresh-token}")
    private String zohoRefreshToken;

    private final RestClient restClient = RestClient.create();

    public synchronized String generateAccessToken() {

        // Step 1: Check whether the cached token is still valid
        if (cachedAccessToken != null &&
                Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {

            return cachedAccessToken;
        }

        // Step 2: Prepare OAuth request parameters
        MultiValueMap<String, String> formData =
                new LinkedMultiValueMap<>();

        formData.add("client_id", zohoClientId);
        formData.add("client_secret", zohoClientSecret);
        formData.add("refresh_token", zohoRefreshToken);
        formData.add("grant_type", "refresh_token");

        // Step 3: Request a new access token
        Map<?, ?> response = restClient.post()
                .uri("https://accounts.zoho.in/oauth/v2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(Map.class);

        // Step 4: Validate the response
        if (response == null || response.get("access_token") == null) {
            throw new RuntimeException(
                    "Failed to generate Catalyst access token");
        }

        // Step 5: Store the token
        cachedAccessToken = response.get("access_token").toString();

        // Step 6: Store token expiry time
        Object expiresInValue = response.get("expires_in");

        long expiresIn = expiresInValue != null
                ? Long.parseLong(expiresInValue.toString())
                : 3600L;

        tokenExpiry = Instant.now().plusSeconds(expiresIn);

        // Step 7: Return the cached token
        return cachedAccessToken;
    }
}
