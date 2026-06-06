package com.bionicpro.auth.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class JwtSubjectExtractor {

    private final ObjectMapper objectMapper;

    public JwtSubjectExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String subject(String accessToken) {
        String[] parts = accessToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Access token is not a JWT");
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode subject = payload.get("sub");
            if (subject == null || subject.asText().isBlank()) {
                throw new IllegalArgumentException("Access token does not contain subject");
            }
            return subject.asText();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Access token payload cannot be decoded", ex);
        }
    }
}
