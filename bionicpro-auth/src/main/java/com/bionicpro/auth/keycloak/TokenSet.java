package com.bionicpro.auth.keycloak;

import java.time.Instant;

public record TokenSet(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String tokenType,
        String scope,
        String tokenBindingId
) {
}
