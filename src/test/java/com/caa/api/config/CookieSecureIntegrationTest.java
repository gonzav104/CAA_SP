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
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el capability "auth-cookie-hardening" del spec de seguridad-backend,
 * caso prod ({@code app.cookie.secure=true}): tanto login como logout deben
 * emitir la cookie con el atributo {@code Secure}, evitando el mismatch entre
 * emisión y limpieza que existía con los dos {@code setSecure(false)} hardcodeados.
 *
 * RED contra el código actual: ambos call sites tienen {@code setSecure(false)}
 * hardcodeado, así que este test debe fallar hasta que ambos lean
 * {@code app.cookie.secure}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caacookiesecure;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        "app.rate-limit.enabled=false",
        "app.cookie.secure=true"
})
@DisplayName("Cookie JWT — app.cookie.secure=true (prod)")
class CookieSecureIntegrationTest {

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
                .email("cookie-secure@ejemplo.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Cookie Secure")
                .rol(RolUsuario.TERAPEUTA)
                .creadoEn(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
        given(usuarioRepository.findByEmail("cookie-secure@ejemplo.com")).willReturn(Optional.of(usuario));
    }

    @Test
    @DisplayName("Login → Set-Cookie con Secure")
    void login_cookieSecureTrue_cookieConSecure() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cookie-secure@ejemplo.com\",\"password\":\"segura123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }

    @Test
    @DisplayName("Logout → Set-Cookie de limpieza también con Secure")
    void logout_cookieSecureTrue_cookieDeLimpiezaConSecure() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }
}
