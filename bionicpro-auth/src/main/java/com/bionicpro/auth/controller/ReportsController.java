package com.bionicpro.auth.controller;

import com.bionicpro.auth.config.AuthProperties;
import com.bionicpro.auth.session.AuthSessionStore;
import com.bionicpro.auth.session.RotatedSession;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportsController {

    private final AuthProperties properties;
    private final AuthSessionStore sessionStore;

    public ReportsController(AuthProperties properties, AuthSessionStore sessionStore) {
        this.properties = properties;
        this.sessionStore = sessionStore;
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, String>> reports(
            @CookieValue(name = "${bionicpro.session.cookie-name}", required = false) String sessionId,
            HttpServletResponse response
    ) {
        return sessionStore.rotateAndEnsureFreshAccessToken(sessionId)
                .map(rotated -> {
                    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(rotated).toString());
                    return ResponseEntity.ok(Map.of(
                            "status", "ready",
                            "message", "Protected report request accepted"
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of(
                        "status", "unauthorized",
                        "message", "Login is required"
                )));
    }

    private ResponseCookie sessionCookie(RotatedSession rotated) {
        return ResponseCookie.from(properties.session().cookieName(), rotated.sessionId())
                .httpOnly(true)
                .secure(properties.session().secureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.session().ttl())
                .build();
    }
}
