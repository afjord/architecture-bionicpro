package com.bionicpro.auth.session;

import java.time.Instant;

public record SessionView(
        boolean authenticated,
        Instant sessionExpiresAt,
        Instant accessTokenExpiresAt
) {
}
