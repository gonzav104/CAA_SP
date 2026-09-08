package com.caa.api.config;

import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        // Este suite NO testea rate limit: deshabilitado (el UsuarioRepository está mockeado,
        // el único POST /auth/login es un body vacío que ni llega al servicio).
        "app.rate-limit.enabled=false"
})
@DisplayName("Seguridad — Tests de integración con MockMvc")
class SecurityIntegrationTest {

    private static final String TEST_SECRET_B64 =
            "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==";
    private static final SecretKey TEST_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET_B64));

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private MockMvc mockMvc;

    private Usuario usuarioTest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        usuarioTest = Usuario.builder()
                .id(UUID.randomUUID())
                .email("integration@ejemplo.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Integration Test")
                .rol(RolUsuario.TERAPEUTA)
                .creadoEn(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    // ──────────────────────────────────────────────
    //  SAD PATHS — Prioritarios
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Endpoint protegido SIN token → devuelve 401")
    void endpointProtegido_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint protegido con token INVÁLIDO (basura) → devuelve 401, NO 500")
    void endpointProtegido_tokenInvalido_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer token-inventado-falso-12345")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint protegido con token VENCIDO → devuelve 401")
    void endpointProtegido_tokenExpirado_devuelve401() throws Exception {
        String tokenExpirado = Jwts.builder()
                .subject("test@ejemplo.com")
                .claim("rol", "TERAPEUTA")
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 7200000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 3600000))
                .signWith(TEST_KEY)
                .compact();

        mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer " + tokenExpirado)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint protegido con Bearer pero token vacío → devuelve 401")
    void endpointProtegido_bearerSinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint protegido con header Authorization sin Bearer → devuelve 401")
    void endpointProtegido_authSinBearer_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Token algo-incorrecto")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    //  HAPPY PATH
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Endpoint protegido con token VÁLIDO → NO devuelve 401 (pasa el filtro de seguridad)")
    void endpointProtegido_tokenValido_noDevuelve401() throws Exception {
        String tokenValido = jwtService.generarToken(usuarioTest);
        given(usuarioRepository.findByEmail("integration@ejemplo.com"))
                .willReturn(Optional.of(usuarioTest));

        // /api/pacientes no existe, pero el filtro de seguridad corre primero.
        // Token válido → pasa el filtro → dispatcher devuelve 404/405, NO 401.
        mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus(),
                        "Token válido no debería devolver 401"));
    }

    @Test
    @DisplayName("Endpoint público /auth/login → NO requiere token (400 por body vacío, no 401)")
    void endpointPublico_authLogin_noRequiereToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Endpoint público /auth/google → NO requiere token (400 por body vacío, no 401)")
    void endpointPublico_authGoogle_noRequiereToken() throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    //  GET /api/usuarios/me
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/usuarios/me SIN token → 401")
    void me_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/usuarios/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/usuarios/me con token VÁLIDO → 200 con datos del usuario")
    void me_tokenValido_devuelve200ConDatos() throws Exception {
        String tokenValido = jwtService.generarToken(usuarioTest);
        given(usuarioRepository.findByEmail("integration@ejemplo.com"))
                .willReturn(Optional.of(usuarioTest));

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuarioTest.getId().toString()))
                .andExpect(jsonPath("$.email").value("integration@ejemplo.com"))
                .andExpect(jsonPath("$.nombre").value("Integration Test"))
                .andExpect(jsonPath("$.rol").value("TERAPEUTA"))
                .andExpect(jsonPath("$.creadoEn").exists());
    }

    @Test
    @DisplayName("GET /api/usuarios/me con token VÁLIDO pero usuario inexistente → 401 genérico")
    void me_tokenValidoUsuarioInexistente_devuelve401() throws Exception {
        String tokenValido = jwtService.generarToken(usuarioTest);
        given(usuarioRepository.findByEmail("integration@ejemplo.com"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Swagger/OpenAPI público → GET /v3/api-docs devuelve 200 sin token y documenta la API")
    void swaggerApiDocs_publico_devuelveOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CAA_SP API")));
    }
}
