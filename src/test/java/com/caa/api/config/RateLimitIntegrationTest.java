package com.caa.api.config;

import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.EmailService;
import com.caa.api.services.RateLimitTimeMeter;
import java.time.Duration;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit de login (max 5) y olvide-password (max 3) por email, ventana de 15 minutos.
 * <p>
 * Cómo se testea la expiración de la ventana sin esperar 15 minutos reales:
 * {@link RateLimitTimeMeter} es el reloj de los buckets (inyectado vía
 * {@code withCustomTimePrecision}); el test lo adelanta con {@code avanzar()}.
 * <p>
 * Cada test usa su PROPIO email (los buckets viven en el singleton del contexto
 * y NO se resetean entre métodos): así no hay interferencia entre tests.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caa_ratelimit;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.max=5",
        "app.rate-limit.login.window-minutes=15",
        "app.rate-limit.olvide-password.max=3",
        "app.rate-limit.olvide-password.window-minutes=15"
})
@DisplayName("Rate limit — Tests de integración con MockMvc")
class RateLimitIntegrationTest {

    private static final String MENSAJE_429 = "Demasiados intentos, esperá unos minutos";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitTimeMeter rateLimitTimeMeter;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private Usuario crearUsuario(String email) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Segura123!"))
                .nombre("Rate Limit Test")
                .rol(RolUsuario.TERAPEUTA)
                .build());
    }

    @Test
    @DisplayName("olvide-password: 3 intentos OK, el 4to en la ventana → 429")
    void olvidePassword_cuartoIntento_devuelve429() throws Exception {
        String email = "olvide@ejemplo.com";
        crearUsuario(email);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/olvide-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(MENSAJE_429));
    }

    @Test
    @DisplayName("login: 5 intentos OK (credenciales válidas), el 6to en la ventana → 429 (un login exitoso también consume cupo)")
    void login_sextoIntento_devuelve429() throws Exception {
        String email = "login@ejemplo.com";
        crearUsuario(email);

        String body = "{\"email\":\"" + email + "\",\"password\":\"Segura123!\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(MENSAJE_429));
    }

    @Test
    @DisplayName("Pasada la ventana de 15 minutos, el cupo se repone y ambos endpoints vuelven a funcionar")
    void pasadoElTiempo_vuelveAFuncionar() throws Exception {
        String email = "tiempo@ejemplo.com";
        crearUsuario(email);

        // Agoto el cupo de olvide-password (3 OK + 4to → 429)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/olvide-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isTooManyRequests());

        // Adelanto el reloj del rate limit más allá de la ventana (15 min + margen)
        rateLimitTimeMeter.avanzar(Duration.ofMinutes(16));

        // Vuelve a funcionar
        mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El 429 es genérico: MISMO cuerpo (salvo timestamp) en login y olvide-password")
    void error429_esGenericoEnAmbosEndpoints() throws Exception {
        String emailOlvide = "generico-olvide@ejemplo.com";
        String emailLogin = "generico-login@ejemplo.com";
        crearUsuario(emailOlvide);
        crearUsuario(emailLogin);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/olvide-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + emailOlvide + "\"}"))
                    .andExpect(status().isOk());
        }
        MvcResult olvide429 = mockMvc.perform(post("/auth/olvide-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailOlvide + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        String loginBody = "{\"email\":\"" + emailLogin + "\",\"password\":\"Segura123!\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isOk());
        }
        MvcResult login429 = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        Map<String, Object> cuerpoOlvide = objectMapper.readValue(
                olvide429.getResponse().getContentAsString(), Map.class);
        Map<String, Object> cuerpoLogin = objectMapper.readValue(
                login429.getResponse().getContentAsString(), Map.class);
        // El timestamp difiere entre respuestas: lo excluimos de la comparación
        cuerpoOlvide.remove("timestamp");
        cuerpoLogin.remove("timestamp");

        assertThat(cuerpoLogin).isEqualTo(cuerpoOlvide);
        assertThat(cuerpoLogin.get("status")).isEqualTo(429);
        assertThat(cuerpoLogin.get("message")).isEqualTo(MENSAJE_429);
    }
}