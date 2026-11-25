package com.example.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZohoUserToken {

    private String userId;
    private String email;
    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;
    private String scope;
    private String tokenType;

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt.minusSeconds(60));
    }
}
