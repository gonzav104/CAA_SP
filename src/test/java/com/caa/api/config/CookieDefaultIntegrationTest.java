package com.caa.api.config;

import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el capability "auth-cookie-hardening" del spec de seguridad-backend,
 * caso por defecto: sin override de {@code app.cookie.secure} ni de
 * {@code jwt.expiration-ms}, el comportamiento debe ser el mismo de siempre
 * (sin {@code Secure}, {@code Max-Age=3600}).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caacookiedefault;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        "app.rate-limit.enabled=false"
})
@DisplayName("Cookie JWT — configuración por defecto (dev)")
class CookieDefaultIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        Usuario usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .email("cookie-default@ejemplo.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Cookie Default")
                .rol(RolUsuario.TERAPEUTA)
                .creadoEn(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
        given(usuarioRepository.findByEmail("cookie-default@ejemplo.com")).willReturn(Optional.of(usuario));
    }

    @Test
    @DisplayName("Login exitoso → Set-Cookie sin Secure y con Max-Age=3600")
    void login_configDefault_cookieSinSecureYMaxAge3600() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cookie-default@ejemplo.com\",\"password\":\"segura123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", not(containsString("Secure"))))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=3600")));
    }
}
