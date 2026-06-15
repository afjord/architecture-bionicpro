package com.bionicpro.auth.session;

import com.bionicpro.auth.config.AuthProperties;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TokenCrypto {

    private static final Logger log = LoggerFactory.getLogger(TokenCrypto.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey key;

    public TokenCrypto(AuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() throws GeneralSecurityException {
        String configuredKey = properties.session().encryptionKey();
        if (StringUtils.hasText(configuredKey)) {
            byte[] decoded = Base64.getDecoder().decode(configuredKey);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("SESSION_ENCRYPTION_KEY must be Base64 encoded 256-bit key");
            }
            this.key = new SecretKeySpec(decoded, "AES");
            return;
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, secureRandom);
        this.key = keyGenerator.generateKey();
        log.warn("SESSION_ENCRYPTION_KEY is not set; generated an ephemeral in-memory key");
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot encrypt token", ex);
        }
    }

    public String decrypt(String encryptedValue) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encryptedValue);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot decrypt token", ex);
        }
    }
}
