package com.orbitcrm.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.orbitcrm.shared.config.OrbitProperties;

/**
 * AES-256-GCM encryption for secrets stored in the database.
 *
 * <p>Purpose: protect secrets at rest so that a database dump alone does not reveal them. Currently used for users'
 * TOTP two-factor seeds; later for integration credentials. The key is the base64-encoded 32-byte value of
 * {@code orbitcrm.security.encryption-key}. Each encryption uses a fresh random 12-byte IV; the output is
 * {@code base64(iv || ciphertext+tag)}, and GCM's authentication tag detects tampering on decryption.
 *
 * @author Abhay
 */
@Component
public class SecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(OrbitProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.security().encryptionKey());
        if (raw.length != 32) {
            throw new IllegalStateException("orbitcrm.security.encryption-key must be 32 bytes, base64 encoded");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Encryption failed", ex);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Decryption failed", ex);
        }
    }
}
