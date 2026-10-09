package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Tarea 6.1: confirma que {@code TokenSeguro.hash} reproduce, byte por byte, el mismo esquema de
 * hashing que {@code AuthService#hashResetToken} (SHA-256, hex en minúsculas) para un input
 * conocido, y que {@code generar()} produce tokens aleatorios, no vacíos y Base64URL válidos.
 */
@DisplayName("TokenSeguro — generación y hashing de tokens de invitación")
class TokenSeguroTest {

    private final TokenSeguro tokenSeguro = new TokenSeguro();

    @Test
    @DisplayName("hash() reproduce el mismo esquema SHA-256 hex que AuthService.hashResetToken")
    void hash_coincideConElEsquemaDeAuthService() throws NoSuchAlgorithmException {
        String tokenConocido = "token-de-prueba-123";

        String esperado = referenciaHashResetToken(tokenConocido);
        String obtenido = tokenSeguro.hash(tokenConocido);

        assertThat(obtenido).isEqualTo(esperado);
    }

    @Test
    @DisplayName("hash() es determinístico: el mismo input produce siempre el mismo hash")
    void hash_esDeterministico() {
        String token = "otro-token-cualquiera";

        assertThat(tokenSeguro.hash(token)).isEqualTo(tokenSeguro.hash(token));
    }

    @Test
    @DisplayName("generar() produce un token Base64URL sin padding, decodificable a 32 bytes")
    void generar_produceTokenBase64UrlDe32Bytes() {
        String token = tokenSeguro.generar();

        assertThat(token).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        byte[] decodificado = Base64.getUrlDecoder().decode(token);
        assertThat(decodificado).hasSize(32);
    }

    @RepeatedTest(20)
    @DisplayName("generar() nunca repite el mismo token en ejecuciones sucesivas")
    void generar_esAleatorio() {
        Set<String> generados = Set.of(tokenSeguro.generar(), tokenSeguro.generar(), tokenSeguro.generar());
        assertThat(generados).hasSize(3);
    }

    /** Reimplementación exacta, aislada, de {@code AuthService#hashResetToken} para comparar. */
    private static String referenciaHashResetToken(String token) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
