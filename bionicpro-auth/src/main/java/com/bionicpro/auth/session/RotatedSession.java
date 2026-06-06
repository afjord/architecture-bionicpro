package com.bionicpro.auth.session;

import java.time.Instant;

public record RotatedSession(
        String sessionId,
        String accessToken,
        Instant sessionExpiresAt
) {
}
