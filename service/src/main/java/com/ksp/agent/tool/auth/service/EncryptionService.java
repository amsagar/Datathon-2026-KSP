package com.ksp.agent.tool.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for tool secrets. Ciphertext is stored as
 * {@code base64(iv):base64(tag):base64(ciphertext)}.
 *
 * The 256-bit key derives from {@code AGENT_ENCRYPTION_KEY} (any length string,
 * SHA-256 reduced to 32 bytes). A built-in dev key is used when unset — fine for
 * local use, not for production.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String DEV_KEY = "codingagent-dev-key-change-me";

    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec keySpec;

    public EncryptionService(@Value("${agent.encryption.key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @PostConstruct
    void init() throws Exception {
        String source = configuredKey == null || configuredKey.isBlank() ? DEV_KEY : configuredKey;
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("AGENT_ENCRYPTION_KEY not set — using built-in dev key. Do not use in production.");
        }
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8));
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] combined = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            int tagBytes = TAG_LENGTH_BITS / 8;
            byte[] ciphertext = new byte[combined.length - tagBytes];
            byte[] tag = new byte[tagBytes];
            System.arraycopy(combined, 0, ciphertext, 0, ciphertext.length);
            System.arraycopy(combined, ciphertext.length, tag, 0, tagBytes);
            Base64.Encoder enc = Base64.getEncoder();
            return enc.encodeToString(iv) + ":" + enc.encodeToString(tag) + ":" + enc.encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            String[] parts = stored.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed ciphertext");
            }
            Base64.Decoder dec = Base64.getDecoder();
            byte[] iv = dec.decode(parts[0]);
            byte[] tag = dec.decode(parts[1]);
            byte[] ciphertext = dec.decode(parts[2]);
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
