package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Contrato HTTP de colaboradores pineado por test (spec colaboradores-api):
 * los errores de auth/permisos son genéricos y NO funcionan como oráculo de
 * enumeración (existencia de cuenta, rol o vínculo). Replica el patrón de
 * CartillaOwnershipIntegrationTest: @SpringBootTest + H2 + repos @MockitoBean
 * + servicio real + MockMvc/springSecurity + JWT real.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caacolab;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret"
})
@DisplayName("Colaboradores — contrato HTTP genérico (404/409 sin oráculo)")
class ColaboradorControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Jackson está en el classpath (convertidores HTTP) pero el contexto no expone
    // un bean ObjectMapper: se instancia directo para parsear los bodies de error.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PacienteRepository pacienteRepository;

    @MockitoBean
    private PacienteFamiliarRepository pacienteFamiliarRepository;

    private MockMvc mockMvc;
    private UUID pacienteId;
    private Usuario terapeuta;
    private Paciente paciente;
    private String tokenTerapeuta;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        pacienteId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(UUID.randomUUID()).email("terapeuta@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta").rol(RolUsuario.TERAPEUTA).build();

        paciente = Paciente.builder()
                .id(pacienteId).terapeuta(terapeuta)
                .nombre("Nico").apellido("Perez").build();

        tokenTerapeuta = jwtService.generarToken(terapeuta);

        // Identidad del principal: la usa el filtro JWT y terapeutaAutenticado
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        // Ownership del paciente (pacienteDelTerapeuta)
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId()))
                .willReturn(Optional.of(paciente));
    }

    private PacienteFamiliar vinculo(Usuario familiar) {
        return PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, familiar.getId()))
                .paciente(paciente)
                .usuario(familiar)
                .permiso(PermisoColaborador.LECTURA)
                .build();
    }

    /** Los bodies de error difieren solo en "timestamp" (LocalDateTime.now por request). */
    private JsonNode sinTimestamp(JsonNode node) {
        return ((ObjectNode) node).without("timestamp");
    }

    // ──────────────────────────────────────────────
    //  POST vincular — 404 genérico (indistinguible)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST email inexistente → 404 \"Usuario no encontrado\" sin el email en el body")
    void post_emailInexistente_404() throws Exception {
        given(usuarioRepository.findByEmail("nadie@test.com")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nadie@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Usuario no encontrado"));
    }

    @Test
    @DisplayName("POST cuenta rol TERAPEUTA → 404 idéntico (400→404 deliberado)")
    void post_cuentaTerapeuta_404() throws Exception {
        Usuario otroTerapeuta = Usuario.builder()
                .id(UUID.randomUUID()).email("otro@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Otro").rol(RolUsuario.TERAPEUTA).build();
        given(usuarioRepository.findByEmail("otro@test.com")).willReturn(Optional.of(otroTerapeuta));

        mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"otro@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Usuario no encontrado"));
    }

    @Test
    @DisplayName("Sin oráculo por iteración: mismo email inexistente y luego TERAPEUTA → bodies idénticos (salvo timestamp)")
    void post_mismoEmail_iterado_bodiesIdenticos() throws Exception {
        Usuario otroTerapeuta = Usuario.builder()
                .id(UUID.randomUUID()).email("nadie@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Otro").rol(RolUsuario.TERAPEUTA).build();
        // Stub secuencial: 1er intento no existe, 2do intento rol inválido
        given(usuarioRepository.findByEmail("nadie@test.com"))
                .willReturn(Optional.empty(), Optional.of(otroTerapeuta));

        String body = "{\"email\": \"nadie@test.com\", \"permiso\": \"LECTURA\"}";

        MvcResult primero = mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuario no encontrado"))
                .andReturn();

        MvcResult segundo = mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuario no encontrado"))
                .andReturn();

        JsonNode body1 = objectMapper.readTree(primero.getResponse().getContentAsString());
        JsonNode body2 = objectMapper.readTree(segundo.getResponse().getContentAsString());

        assertThat(body1.get("status").asInt()).isEqualTo(404);
        assertThat(body2.get("status").asInt()).isEqualTo(404);
        assertThat(sinTimestamp(body1)).isEqualTo(sinTimestamp(body2));
    }

    // ──────────────────────────────────────────────
    //  POST vincular — 409 texto neutro
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST familiar ya vinculado → 409 \"No se puede vincular este usuario\"")
    void post_familiarYaVinculado_409() throws Exception {
        Usuario familiar = Usuario.builder()
                .id(UUID.randomUUID()).email("mama@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Mama").rol(RolUsuario.FAMILIAR).build();
        given(usuarioRepository.findByEmail("mama@test.com")).willReturn(Optional.of(familiar));
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiar.getId()))
                .willReturn(Optional.of(vinculo(familiar)));

        mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"mama@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("No se puede vincular este usuario"));
    }

    // ──────────────────────────────────────────────
    //  PUT/DELETE — vínculo inexistente → 404
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT usuarioId sin vínculo → 404 \"Usuario no encontrado\"")
    void put_usuarioSinVinculo_404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId))
                .willReturn(Optional.empty());

        mockMvc.perform(put("/api/pacientes/{pacienteId}/colaboradores/{usuarioId}", pacienteId, usuarioId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permiso\": \"EDICION_LIMITADA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuario no encontrado"));
    }

    @Test
    @DisplayName("DELETE usuarioId sin vínculo → 404 \"Usuario no encontrado\"")
    void delete_usuarioSinVinculo_404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId))
                .willReturn(Optional.empty());

        mockMvc.perform(delete("/api/pacientes/{pacienteId}/colaboradores/{usuarioId}", pacienteId, usuarioId)
                        .header("Authorization", "Bearer " + tokenTerapeuta))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuario no encontrado"));
    }
}