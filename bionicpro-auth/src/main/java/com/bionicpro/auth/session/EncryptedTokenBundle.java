package com.bionicpro.auth.session;

import java.time.Instant;

public record EncryptedTokenBundle(
        String encryptedAccessToken,
        String encryptedRefreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String tokenType,
        String scope,
        String tokenBindingId,
        Instant sessionExpiresAt
) {
}
