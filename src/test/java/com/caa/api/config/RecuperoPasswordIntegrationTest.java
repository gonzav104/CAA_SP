package com.caa.api.config;

import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.EmailService;
import java.time.LocalDateTime;
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
        "resend.api-key=test-resend-api-key"
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

        // En BD se guarda el MISMO token plano que viaja por email.
        Usuario persistido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(persistido.getResetToken()).isEqualTo(tokenEnviado);
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
    @DisplayName("restablecer-password con token VÁLIDO → cambia la contraseña y limpia el token")
    void restablecerPassword_tokenValido_cambiaPassword() throws Exception {
        String token = UUID.randomUUID().toString();
        usuario.setResetToken(token);
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
    }

    @Test
    @DisplayName("restablecer-password con token EXPIRADO → 404 genérico (indistinguible del inexistente)")
    void restablecerPassword_tokenExpirado_errorGenerico() throws Exception {
        String token = UUID.randomUUID().toString();
        usuario.setResetToken(token);
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
        usuario.setResetToken(token);
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
}