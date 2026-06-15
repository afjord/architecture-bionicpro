package com.bionicpro.auth.controller;

import com.bionicpro.auth.config.AuthProperties;
import com.bionicpro.auth.keycloak.KeycloakClient;
import com.bionicpro.auth.keycloak.TokenSet;
import com.bionicpro.auth.session.AuthSessionStore;
import com.bionicpro.auth.session.LoginStateStore.LoginState;
import com.bionicpro.auth.session.LoginStateStore;
import com.bionicpro.auth.session.SessionView;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthProperties properties;
    private final KeycloakClient keycloakClient;
    private final AuthSessionStore sessionStore;
    private final LoginStateStore loginStateStore;

    public AuthController(
            AuthProperties properties,
            KeycloakClient keycloakClient,
            AuthSessionStore sessionStore,
            LoginStateStore loginStateStore
    ) {
        this.properties = properties;
        this.keycloakClient = keycloakClient;
        this.sessionStore = sessionStore;
        this.loginStateStore = loginStateStore;
    }

    @GetMapping("/auth/login")
    public ResponseEntity<Void> login() {
        LoginState loginState = loginStateStore.create();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(keycloakClient.authorizationUri(loginState.state(), loginState.codeVerifier()))
                .build();
    }

    @GetMapping("/auth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        if (error != null || code == null || state == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        LoginState loginState = loginStateStore.consume(state);
        if (loginState == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        TokenSet tokenSet = keycloakClient.exchangeCode(code, loginState.codeVerifier());
        String sessionId = sessionStore.create(tokenSet);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, sessionCookie(sessionId).toString())
                .location(URI.create(properties.frontendUrl()))
                .build();
    }

    @GetMapping("/auth/session")
    public ResponseEntity<SessionView> session(
            @CookieValue(name = "${bionicpro.session.cookie-name}", required = false) String sessionId
    ) {
        return sessionStore.view(sessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Boolean>> logout(
            @CookieValue(name = "${bionicpro.session.cookie-name}", required = false) String sessionId
    ) {
        if (sessionId != null) {
            sessionStore.remove(sessionId);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString())
                .body(Map.of("authenticated", false));
    }

    private ResponseCookie sessionCookie(String sessionId) {
        return ResponseCookie.from(properties.session().cookieName(), sessionId)
                .httpOnly(true)
                .secure(properties.session().secureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.session().ttl())
                .build();
    }

    private ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(properties.session().cookieName(), "")
                .httpOnly(true)
                .secure(properties.session().secureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
