package com.bionicpro.auth.keycloak;

import com.bionicpro.auth.config.AuthProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KeycloakClient {

    private final AuthProperties properties;
    private final WebClient webClient;

    public KeycloakClient(AuthProperties properties, WebClient keycloakWebClient) {
        this.properties = properties;
        this.webClient = keycloakWebClient;
    }

    public URI authorizationUri(String state, String codeVerifier) {
        return UriComponentsBuilder
                .fromUriString(properties.keycloak().browserAuthorizationEndpoint())
                .queryParam("client_id", properties.keycloak().clientId())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email")
                .queryParam("redirect_uri", properties.keycloak().redirectUri())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUri();
    }

    public TokenSet exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("redirect_uri", properties.keycloak().redirectUri());
        return tokenRequest(form);
    }

    public TokenSet refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return tokenRequest(form);
    }

    public void logout(String refreshToken) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("refresh_token", refreshToken);

        webClient.post()
                .uri(properties.keycloak().logoutEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .toBodilessEntity()
                .onErrorComplete()
                .block();
    }

    private TokenSet tokenRequest(MultiValueMap<String, String> form) {
        KeycloakTokenResponse response = webClient.post()
                .uri(properties.keycloak().tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(KeycloakTokenResponse.class)
                .block();

        if (response == null || response.accessToken() == null || response.refreshToken() == null) {
            throw new IllegalStateException("Keycloak token response does not contain required tokens");
        }

        Instant now = Instant.now();
        return new TokenSet(
                response.accessToken(),
                response.refreshToken(),
                now.plusSeconds(response.expiresIn()),
                now.plusSeconds(response.refreshExpiresIn()),
                response.tokenType(),
                response.scope(),
                UUID.randomUUID().toString()
        );
    }

    private MultiValueMap<String, String> baseClientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.keycloak().clientId());
        form.add("client_secret", properties.keycloak().clientSecret());
        return form;
    }

    private String codeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for PKCE", ex);
        }
    }
}
