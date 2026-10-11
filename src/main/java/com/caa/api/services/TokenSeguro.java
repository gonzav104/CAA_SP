package com.caa.api.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Generación y hashing de tokens de invitación (design §9.1). El token crudo nunca se persiste;
 * solo su hash SHA-256 viaja a la base de datos ({@code invitaciones.token_hash}).
 * <p>
 * {@link #hash(String)} usa EXACTAMENTE la misma codificación que
 * {@code AuthService#hashResetToken} (SHA-256, hex en minúsculas) para mantener un único idiom
 * de hashing de tokens en todo el proyecto.
 */
@Component
public class TokenSeguro {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    /** Token crudo: 32 bytes de {@link SecureRandom}, Base64URL sin padding. */
    public String generar() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hex (minúsculas) del token plano — idéntico a {@code AuthService#hashResetToken}. */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en este JVM", e);
        }
    }
}
