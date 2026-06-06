package com.bionicpro.auth.session;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LoginStateStore {

    private static final long STATE_TTL_SECONDS = 300;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, LoginState> states = new ConcurrentHashMap<>();

    public LoginState create() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String codeVerifier = randomToken();
        LoginState loginState = new LoginState(state, codeVerifier, Instant.now().plusSeconds(STATE_TTL_SECONDS));
        states.put(state, loginState);
        return loginState;
    }

    public LoginState consume(String state) {
        LoginState loginState = states.remove(state);
        if (loginState == null || !loginState.expiresAt().isAfter(Instant.now())) {
            return null;
        }
        return loginState;
    }

    private String randomToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record LoginState(String state, String codeVerifier, Instant expiresAt) {
    }
}
