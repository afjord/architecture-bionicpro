package com.bionicpro.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bionicpro")
public record AuthProperties(
        @NotBlank String frontendUrl,
        @NotNull Session session,
        @NotNull Keycloak keycloak,
        @NotNull ReportStorage reportStorage
) {
    public record Session(
            @NotBlank String cookieName,
            @NotNull Duration ttl,
            boolean secureCookie,
            String encryptionKey
    ) {
    }

    public record Keycloak(
            @NotBlank String issuerUri,
            @NotBlank String authServerUrl,
            @NotBlank String internalAuthServerUrl,
            @NotBlank String realm,
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotBlank String redirectUri
    ) {
        public String browserAuthorizationEndpoint() {
            return authServerUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
        }

        public String tokenEndpoint() {
            return internalAuthServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        }

        public String logoutEndpoint() {
            return internalAuthServerUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
        }
    }

    public record ReportStorage(
            @NotBlank String endpoint,
            @NotBlank String region,
            @NotBlank String accessKey,
            @NotBlank String secretKey,
            @NotBlank String bucket,
            @NotBlank String cdnBaseUrl
    ) {
    }
}
