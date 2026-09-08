package com.caa.api.services;

import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService — Tests unitarios")
class JwtServiceTest {

    // Mismo formato que .env: clave raw UTF-8 (sin Base64)
    // 64+ caracteres → 512+ bits → JJWT usa HS512 automáticamente
    private static final String TEST_SECRET =
            "Xk9mP2vL8nQ4wR7tY5uB3jH6fC1aD0eGhiNoPqRsTuVwXyZabCdEfGhIjKlMn";
    private static final long EXPIRATION_MS = 3600000; // 1 hora

    private JwtService jwtService;
    private SecretKey testKey;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
        // Misma lógica que JwtService: bytes raw UTF-8, sin Base64
        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .email("test@ejemplo.com")
                .passwordHash("$2a$10$hash")
                .nombre("Test User")
                .rol(RolUsuario.FAMILIAR)
                .build();
    }

    // ──────────────────────────────────────────────
    //  SAD PATHS — Casos de fallo
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Token expirado → lanza RuntimeException con mensaje claro")
    void validarYObtenerEmail_tokenExpirado_lanzaExcepcion() {
        // Arrange: creo un token que ya expiró (exp en el pasado)
        String tokenExpirado = Jwts.builder()
                .subject("test@ejemplo.com")
                .claim("rol", "FAMILIAR")
                .issuedAt(new Date(System.currentTimeMillis() - 7200000)) // hace 2 horas
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // expiró hace 1 hora
                .signWith(testKey)
                .compact();

        // Act & Assert
        assertThatThrownBy(() -> jwtService.validarYObtenerEmail(tokenExpirado))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("Token con firma inválida (alterado) → lanza RuntimeException con mensaje claro")
    void validarYObtenerEmail_firmaInvalida_lanzaExcepcion() {
        // Arrange: genero un token válido y le cambio los últimos caracteres
        String tokenValido = jwtService.generarToken(usuario);
        String tokenAlterado = tokenValido.substring(0, tokenValido.length() - 5) + "XXXXX";

        // Act & Assert
        assertThatThrownBy(() -> jwtService.validarYObtenerEmail(tokenAlterado))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("firma");
    }

    @Test
    @DisplayName("Token firmado con otra clave → lanza RuntimeException")
    void validarYObtenerEmail_otraClave_lanzaExcepcion() {
        // Arrange: firmo con una clave diferente (raw UTF-8, mismo formato)
        String otraClave = "OtraClaveSecretaParaTests1234567890abcdefGhIjKlMnOpQrStUv";
        SecretKey otraKey = Keys.hmacShaKeyFor(otraClave.getBytes(StandardCharsets.UTF_8));

        String tokenConOtraClave = Jwts.builder()
                .subject("test@ejemplo.com")
                .claim("rol", "FAMILIAR")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(otraKey)
                .compact();

        // Act & Assert
        assertThatThrownBy(() -> jwtService.validarYObtenerEmail(tokenConOtraClave))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("firma");
    }

    @Test
    @DisplayName("Token malformado (string basura) → lanza RuntimeException")
    void validarYObtenerEmail_tokenBasura_lanzaExcepcion() {
        assertThatThrownBy(() -> jwtService.validarYObtenerEmail("esto-no-es-un-token"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Token null o vacío → lanza RuntimeException")
    void validarYObtenerEmail_tokenNulo_lanzaExcepcion() {
        assertThatThrownBy(() -> jwtService.validarYObtenerEmail(null))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> jwtService.validarYObtenerEmail(""))
                .isInstanceOf(RuntimeException.class);
    }

    // ──────────────────────────────────────────────
    //  HAPPY PATH
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("generarToken → token tiene subject = email, claim 'rol' y claim tokenVersion")
    void generarToken_contieneEmailYRol() {
        String token = jwtService.generarToken(usuario);

        assertThat(token).isNotBlank();

        var claims = Jwts.parser()
                .verifyWith(testKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("test@ejemplo.com");
        assertThat(claims.get("rol", String.class)).isEqualTo("FAMILIAR");
        // El builder de Usuario arranca en 0 (@Builder.Default)
        assertThat(claims.get("tokenVersion", Number.class).intValue()).isZero();
    }

    @Test
    @DisplayName("generarToken → el claim tokenVersion refleja el valor ACTUAL del Usuario")
    void generarToken_tokenVersionReflejaElValorDelUsuario() {
        usuario.setTokenVersion(7);

        String token = jwtService.generarToken(usuario);

        var claims = Jwts.parser()
                .verifyWith(testKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get("tokenVersion", Number.class).intValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("generarToken con tokenVersion null (datos viejos) → claim tokenVersion = 0 (defensivo)")
    void generarToken_tokenVersionNull_seTrataComoCero() {
        usuario.setTokenVersion(null);

        String token = jwtService.generarToken(usuario);

        var claims = Jwts.parser()
                .verifyWith(testKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get("tokenVersion", Number.class).intValue()).isZero();
    }

    @Test
    @DisplayName("validarYObtenerEmail con token válido → devuelve el email")
    void validarYObtenerEmail_tokenValido_devuelveEmail() {
        String token = jwtService.generarToken(usuario);

        String email = jwtService.validarYObtenerEmail(token);

        assertThat(email).isEqualTo("test@ejemplo.com");
    }
}
