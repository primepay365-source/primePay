package com.primepay.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Encrypts sensitive fields (saved bank account numbers) with AES-256-GCM, and makes a keyed hash for
 * duplicate checks without decrypting. The key comes from FIELD_ENCRYPTION_KEY_BASE64 (32 bytes).
 * The dev profile may use a random per-process key, because its database is also discarded on restart.
 */
@Component
public class FieldEncryptor {
    private static final Logger log = LoggerFactory.getLogger(FieldEncryptor.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(@Value("${app.security.field-key-base64:}") String keyBase64, Environment environment) {
        byte[] bytes;
        if (keyBase64 == null || keyBase64.isBlank()) {
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("Set FIELD_ENCRYPTION_KEY_BASE64 to 32 random bytes (Base64).");
            }
            bytes = new byte[32];
            random.nextBytes(bytes);
            log.warn("Using an ephemeral field-encryption key for the dev profile; saved bank accounts are unreadable after restart.");
        } else {
            bytes = Base64.getDecoder().decode(keyBase64);
            if (bytes.length != 32) {
                throw new IllegalStateException("FIELD_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes.");
            }
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    /** Deterministic keyed hash (HMAC-SHA256) for finding duplicates without storing the plain value. */
    public String hash(String plain) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Hashing failed", e);
        }
    }
}
