package com.example.ecommerce;

import com.example.ecommerce.model.ZohoUserToken;
import com.example.ecommerce.service.ZohoTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZohoTokenServiceTest {

    private ZohoTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new ZohoTokenService();
    }

    @Test
    void saveAndGetToken_WhenSaved_ReturnsToken() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        tokenService.saveToken("user-1", "user1@example.com", "access-1", "refresh-1", expiresAt, "openid,email");

        assertTrue(tokenService.hasToken("user-1"));
        Optional<ZohoUserToken> token = tokenService.getToken("user-1");
        assertTrue(token.isPresent());
        assertEquals("access-1", token.get().getAccessToken());
        assertEquals("refresh-1", token.get().getRefreshToken());
        assertEquals("user1@example.com", token.get().getEmail());
        assertFalse(token.get().isExpired());
    }

    @Test
    void multipleUsers_StoreTokensIndependently() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        tokenService.saveToken("user-1", "user1@example.com", "access-1", "refresh-1", expiresAt, "openid");
        tokenService.saveToken("user-2", "user2@example.com", "access-2", "refresh-2", expiresAt, "openid");

        assertEquals("access-1", tokenService.getToken("user-1").get().getAccessToken());
        assertEquals("access-2", tokenService.getToken("user-2").get().getAccessToken());

        tokenService.removeToken("user-1");
        assertFalse(tokenService.hasToken("user-1"));
        assertTrue(tokenService.hasToken("user-2"));
    }

    @Test
    void isExpired_WhenWithin60Seconds_ReturnsTrue() {
        // Expiring in 30 seconds -> within 60 second buffer -> isExpired() should be true
        Instant nearExpiry = Instant.now().plusSeconds(30);
        ZohoUserToken token = ZohoUserToken.builder()
                .userId("u1")
                .accessToken("acc")
                .refreshToken("ref")
                .expiresAt(nearExpiry)
                .build();

        assertTrue(token.isExpired());
    }

    @Test
    void getValidAccessToken_WhenNotExpired_ReturnsTokenDirectly() {
        Instant farExpiry = Instant.now().plusSeconds(3600);
        tokenService.saveToken("user-1", "user1@example.com", "valid-access-token", "refresh-1", farExpiry, "openid");

        String validToken = tokenService.getValidAccessToken("user-1");
        assertEquals("valid-access-token", validToken);
    }
}
