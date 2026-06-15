package com.bionicpro.auth.session;

import com.bionicpro.auth.config.AuthProperties;
import com.bionicpro.auth.keycloak.KeycloakClient;
import com.bionicpro.auth.keycloak.TokenSet;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class AuthSessionStore {

    private static final long ACCESS_TOKEN_REFRESH_SKEW_SECONDS = 10;

    private final AuthProperties properties;
    private final KeycloakClient keycloakClient;
    private final TokenCrypto tokenCrypto;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, EncryptedTokenBundle> sessions = new ConcurrentHashMap<>();

    public AuthSessionStore(AuthProperties properties, KeycloakClient keycloakClient, TokenCrypto tokenCrypto) {
        this.properties = properties;
        this.keycloakClient = keycloakClient;
        this.tokenCrypto = tokenCrypto;
    }

    public String create(TokenSet tokenSet) {
        String sessionId = newSessionId();
        sessions.put(sessionId, encrypt(tokenSet, sessionExpiresAt()));
        return sessionId;
    }

    public Optional<SessionView> view(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        EncryptedTokenBundle bundle = sessions.get(sessionId);
        if (bundle == null || isExpired(bundle.sessionExpiresAt())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(new SessionView(true, bundle.sessionExpiresAt(), bundle.accessTokenExpiresAt()));
    }

    public Optional<RotatedSession> rotateAndEnsureFreshAccessToken(String currentSessionId) {
        if (currentSessionId == null) {
            return Optional.empty();
        }

        EncryptedTokenBundle bundle = sessions.remove(currentSessionId);
        if (bundle == null || isExpired(bundle.sessionExpiresAt()) || isExpired(bundle.refreshTokenExpiresAt())) {
            return Optional.empty();
        }

        TokenSet tokens = decrypt(bundle);
        if (tokens.accessTokenExpiresAt().minusSeconds(ACCESS_TOKEN_REFRESH_SKEW_SECONDS).isBefore(Instant.now())) {
            tokens = keycloakClient.refresh(tokens.refreshToken());
        }

        String newSessionId = newSessionId();
        EncryptedTokenBundle rotated = encrypt(tokens, sessionExpiresAt());
        sessions.put(newSessionId, rotated);
        return Optional.of(new RotatedSession(newSessionId, tokens.accessToken(), rotated.sessionExpiresAt()));
    }

    public void remove(String sessionId) {
        EncryptedTokenBundle removed = sessions.remove(sessionId);
        if (removed != null) {
            keycloakClient.logout(tokenCrypto.decrypt(removed.encryptedRefreshToken()));
        }
    }

    private EncryptedTokenBundle encrypt(TokenSet tokenSet, Instant sessionExpiresAt) {
        return new EncryptedTokenBundle(
                tokenCrypto.encrypt(tokenSet.accessToken()),
                tokenCrypto.encrypt(tokenSet.refreshToken()),
                tokenSet.accessTokenExpiresAt(),
                tokenSet.refreshTokenExpiresAt(),
                tokenSet.tokenType(),
                tokenSet.scope(),
                tokenSet.tokenBindingId(),
                sessionExpiresAt
        );
    }

    private TokenSet decrypt(EncryptedTokenBundle bundle) {
        return new TokenSet(
                tokenCrypto.decrypt(bundle.encryptedAccessToken()),
                tokenCrypto.decrypt(bundle.encryptedRefreshToken()),
                bundle.accessTokenExpiresAt(),
                bundle.refreshTokenExpiresAt(),
                bundle.tokenType(),
                bundle.scope(),
                bundle.tokenBindingId()
        );
    }

    private Instant sessionExpiresAt() {
        return Instant.now().plus(properties.session().ttl());
    }

    private boolean isExpired(Instant expiresAt) {
        return !expiresAt.isAfter(Instant.now());
    }

    private String newSessionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
