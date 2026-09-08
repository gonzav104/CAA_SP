package com.caa.api.config;

import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caa_recupero;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        // Este suite NO testea rate limit: deshabilitado para que el cupo en memoria
        // no se acumule entre tests (los olvide-password repetidos con el mismo email).
        "app.rate-limit.enabled=false"
})
@DisplayName("Recupero de contraseña — Tests de integración con MockMvc")
@Transactional
class RecuperoPasswordIntegrationTest {

    private static final String MENSAJE_EXITO_OLVIDE =
            "Si el email está registrado, vas a recibir un enlace para restablecer tu contraseña";
    private static final String MENSAJE_ENLACE_INVALIDO = "El enlace no es válido o ha expirado";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        usuario = usuarioRepository.save(Usuario.builder()
                .email("recupero@ejemplo.com")
                .passwordHash(passwordEncoder.encode("Segura123!"))
                .nombre("Test Recupero")
                .rol(RolUsuario.TERAPEUTA)
                .build());
    }

    // ──────────────────────────────────────────────
    //  POST /auth/olvide-password
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("olvide-password con email EXISTENTE → 200, genera token con expiración y envía email")
    void olvidePassword_emailExistente_generaTokenYEnviaEmail() throws Exception {
        mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recupero@ejemplo.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MENSAJE_EXITO_OLVIDE));

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarRecuperacionPassword(usuarioCaptor.capture(), tokenCaptor.capture());

        String tokenEnviado = tokenCaptor.getValue();
        assertThat(tokenEnviado).isNotBlank();

        // En BD se guarda SOLO el hash SHA-256 del token (el plano nunca se persiste)
        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(persistido.getResetToken()).isEqualTo(hashSha256(tokenEnviado));
        assertThat(persistido.getResetToken()).isNotEqualTo(tokenEnviado);
        assertThat(persistido.getResetTokenExpira()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("olvide-password con email INEXISTENTE → misma respuesta exacta, sin enviar email ni crear nada")
    void olvidePassword_emailInexistente_mismaRespuestaSinEmail() throws Exception {
        MvcResult inexistente = mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noexiste@ejemplo.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MENSAJE_EXITO_OLVIDE))
                .andReturn();

        // El email inexistente no dispara ningún envío ni se guarda en ningún lado
        verify(emailService, never()).enviarRecuperacionPassword(
                org.mockito.ArgumentMatchers.any(Usuario.class),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(usuarioRepository.findByEmail("noexiste@ejemplo.com")).isEmpty();

        // Misma respuesta que el caso existente (no distinguible)
        MvcResult existente = mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recupero@ejemplo.com\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(inexistente.getResponse().getContentAsString())
                .isEqualTo(existente.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("olvide-password sin token de auth → NO requiere autenticación (400 por body vacío, no 401)")
    void olvidePassword_sinAuth_noRequiereToken() throws Exception {
        mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    //  POST /auth/restablecer-password
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("restablecer-password con token VÁLIDO → cambia la contraseña, limpia el token y sube tokenVersion")
    void restablecerPassword_tokenValido_cambiaPassword() throws Exception {
        String token = UUID.randomUUID().toString();
        // El token se guardó hasheado (así lo persiste olvide-password)
        usuario.setResetToken(hashSha256(token));
        usuario.setResetTokenExpira(LocalDateTime.now().plusHours(1));
        usuarioRepository.save(usuario);

        mockMvc.perform(post("/auth/restablecer-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"NuevaClave1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tu contraseña fue restablecida correctamente"));

        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NuevaClave1!", persistido.getPasswordHash())).isTrue();
        assertThat(persistido.getResetToken()).isNull();
        assertThat(persistido.getResetTokenExpira()).isNull();
        // Restablecer la contraseña invalida TODAS las sesiones activas
        assertThat(persistido.getTokenVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("restablecer-password con token EXPIRADO → 404 genérico (indistinguible del inexistente)")
    void restablecerPassword_tokenExpirado_errorGenerico() throws Exception {
        String token = UUID.randomUUID().toString();
        usuario.setResetToken(hashSha256(token));
        usuario.setResetTokenExpira(LocalDateTime.now().minusHours(1));
        usuarioRepository.save(usuario);

        mockMvc.perform(post("/auth/restablecer-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"NuevaClave1!\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(MENSAJE_ENLACE_INVALIDO));

        // La contraseña NO cambió
        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NuevaClave1!", persistido.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("restablecer-password con token INEXISTENTE → 404 con el MISMO mensaje que el expirado")
    void restablecerPassword_tokenInexistente_mismoError() throws Exception {
        mockMvc.perform(post("/auth/restablecer-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-que-no-existe\",\"password\":\"NuevaClave1!\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(MENSAJE_ENLACE_INVALIDO));
    }

    @Test
    @DisplayName("restablecer-password con password DÉBIL → 400 con la misma validación que el registro")
    void restablecerPassword_passwordDebil_mismoErrorQueRegistro() throws Exception {
        String token = UUID.randomUUID().toString();
        usuario.setResetToken(hashSha256(token));
        usuario.setResetTokenExpira(LocalDateTime.now().plusHours(1));
        usuarioRepository.save(usuario);

        mockMvc.perform(post("/auth/restablecer-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"abc123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(
                                "La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo")));
    }

    // ──────────────────────────────────────────────
    //  TOKEN VERSION (invalidar sesiones activas al restablecer)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Un usuario recién registrado arranca con tokenVersion=0")
    void usuarioNuevo_arrancaConTokenVersionCero() {
        Usuario recienCreado = usuarioRepository.save(Usuario.builder()
                .email("recien@ejemplo.com")
                .passwordHash(passwordEncoder.encode("Segura123!"))
                .nombre("Recién Creado")
                .rol(RolUsuario.FAMILIAR)
                .build());

        assertThat(recienCreado.getTokenVersion()).isZero();
    }

    @Test
    @DisplayName("Restablecer la contraseña invalida las sesiones activas: el token VIEJO no autentica, el NUEVO sí")
    void restablecerPassword_invalidaSesionesActivas() throws Exception {
        // 1. Login con la contraseña actual → token VIEJO (versión 0)
        MvcResult loginViejo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recupero@ejemplo.com\",\"password\":\"Segura123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String tokenViejo = loginViejo.getResponse().getCookie("jwt").getValue();
        assertThat(tokenViejo).isNotBlank();

        // El token viejo autentica ANTES de restablecer
        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + tokenViejo))
                .andExpect(status().isOk());

        // 2. Flujo de recupero: token + hash en BD (como lo guarda olvide-password)
        String resetToken = UUID.randomUUID().toString();
        usuario.setResetToken(hashSha256(resetToken));
        usuario.setResetTokenExpira(LocalDateTime.now().plusHours(1));
        usuarioRepository.save(usuario);

        // 3. Restablecer la contraseña (incrementa tokenVersion)
        mockMvc.perform(post("/auth/restablecer-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"password\":\"NuevaClave1!\"}"))
                .andExpect(status().isOk());

        // 4. El token VIEJO ya NO autentica en un endpoint protegido (misma respuesta que un token inválido)
        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + tokenViejo))
                .andExpect(status().isUnauthorized());

        // 5. Login de nuevo → token NUEVO (versión 1) que SÍ funciona
        MvcResult loginNuevo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recupero@ejemplo.com\",\"password\":\"NuevaClave1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String tokenNuevo = loginNuevo.getResponse().getCookie("jwt").getValue();
        assertThat(tokenNuevo).isNotBlank();

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + tokenNuevo))
                .andExpect(status().isOk());
    }

    /**
     * Misma codificación que AuthService.hashResetToken: SHA-256 en hex.
     * Si el helper de producción cambiara, estos asserts fallarían.
     */
    private static String hashSha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}