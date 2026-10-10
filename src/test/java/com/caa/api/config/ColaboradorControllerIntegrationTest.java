package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Invitacion;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.InvitacionRepository;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.EmailService;
import com.caa.api.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Contrato HTTP de colaboradores (spec {@code patient-collaborators} MODIFICADA): agregar un
 * colaborador ya NO vincula directamente — crea una invitación (202) y responde con la MISMA
 * forma sea el email destino ya tenga cuenta o no (no-enumeración). {@code ColaboradorServiceImpl},
 * {@code InvitacionServiceImpl} y {@code AccesoServiceImpl} reales; solo los repositorios y
 * {@code EmailService} se mockean.
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
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("Colaboradores — contrato HTTP (invitación, 202, sin oráculo de cuenta)")
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

    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private PacienteRepository pacienteRepository;
    @MockitoBean private MembresiaRepository membresiaRepository;
    @MockitoBean private PacienteTerapeutaRepository pacienteTerapeutaRepository;
    @MockitoBean private PacienteFamiliarRepository pacienteFamiliarRepository;
    @MockitoBean private InvitacionRepository invitacionRepository;
    @MockitoBean private EmailService emailService;

    private MockMvc mockMvc;
    private UUID organizacionId;
    private UUID pacienteId;
    private Usuario terapeuta;
    private Organizacion organizacion;
    private Paciente paciente;
    private String tokenTerapeuta;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        organizacionId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(UUID.randomUUID()).email("terapeuta@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta").build();

        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").creadoPor(terapeuta).build();

        paciente = Paciente.builder()
                .id(pacienteId).organizacion(organizacion)
                .nombre("Nico").apellido("Perez").build();

        tokenTerapeuta = jwtService.generarToken(terapeuta);

        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, terapeuta.getId())))
                .willReturn(Optional.of(membresiaOwner()));

        // InvitacionRepository real guardaría en H2 y violaría el FK de paciente_id (el Paciente
        // de este test es un stub de Mockito, nunca persistido); se mockea con un "echo" que
        // completa id/creadoEn como lo haría la base de datos real, sin tocar H2.
        given(invitacionRepository.saveAndFlush(any(Invitacion.class))).willAnswer(invocacion -> {
            Invitacion invitacion = invocacion.getArgument(0);
            if (invitacion.getId() == null) {
                invitacion.setId(UUID.randomUUID());
            }
            if (invitacion.getCreadoEn() == null) {
                invitacion.setCreadoEn(LocalDateTime.now());
            }
            return invitacion;
        });
    }

    private Membresia membresiaOwner() {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeuta.getId()))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build();
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
    //  POST vincular — ahora 202 (invitación), no 201
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST a un email sin cuenta → 202, invitación PENDIENTE, sin crear ningún Usuario")
    void post_emailSinCuenta_202() throws Exception {
        given(usuarioRepository.findByEmail("nadie@test.com")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nadie@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tipo").value("PACIENTE_FAMILIAR"))
                .andExpect(jsonPath("$.email").value("nadie@test.com"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.permisoPropuesto").value("LECTURA"));
    }

    @Test
    @DisplayName("POST a un email con cuenta ya existente → 202, misma forma")
    void post_emailConCuentaExistente_202() throws Exception {
        Usuario existente = Usuario.builder()
                .id(UUID.randomUUID()).email("existente@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Existente").build();
        given(usuarioRepository.findByEmail("existente@test.com")).willReturn(Optional.of(existente));

        mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"existente@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tipo").value("PACIENTE_FAMILIAR"))
                .andExpect(jsonPath("$.email").value("existente@test.com"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("Sin oráculo de cuenta: email inexistente y email con cuenta → bodies idénticos (salvo email/timestamp)")
    void post_sinOraculoDeCuenta_bodiesIdenticos() throws Exception {
        Usuario existente = Usuario.builder()
                .id(UUID.randomUUID()).email("otro@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Otro").build();
        given(usuarioRepository.findByEmail("inexistente@test.com")).willReturn(Optional.empty());
        given(usuarioRepository.findByEmail("otro@test.com")).willReturn(Optional.of(existente));

        MvcResult sinCuenta = mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"inexistente@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult conCuenta = mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"otro@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isAccepted())
                .andReturn();

        ObjectNode bodySinCuenta = (ObjectNode) sinTimestamp(objectMapper.readTree(sinCuenta.getResponse().getContentAsString()));
        ObjectNode bodyConCuenta = (ObjectNode) sinTimestamp(objectMapper.readTree(conCuenta.getResponse().getContentAsString()));
        // "id" difiere siempre (UUID nuevo por invitación); "email" es la única diferencia esperada.
        bodySinCuenta.remove("id");
        bodyConCuenta.remove("id");
        bodySinCuenta.remove("email");
        bodyConCuenta.remove("email");
        bodySinCuenta.remove("expiraEn");
        bodyConCuenta.remove("expiraEn");

        assertThat(bodySinCuenta).isEqualTo(bodyConCuenta);
    }

    @Test
    @DisplayName("POST sin acceso al paciente (no es miembro de la organización) → 404 genérico")
    void post_sinAcceso_404() throws Exception {
        given(membresiaRepository.findById(new MembresiaId(organizacionId, terapeuta.getId())))
                .willReturn(Optional.empty());
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, terapeuta.getId()))
                .willReturn(Optional.empty());

        mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nadie@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST a quien ya es colaborador del paciente → 409 \"No se puede invitar a este usuario\"")
    void post_yaEsColaborador_409() throws Exception {
        Usuario familiar = Usuario.builder()
                .id(UUID.randomUUID()).email("mama@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Mama").build();
        given(usuarioRepository.findByEmail("mama@test.com")).willReturn(Optional.of(familiar));
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiar.getId()))
                .willReturn(Optional.of(vinculo(familiar)));

        mockMvc.perform(post("/api/pacientes/{pacienteId}/colaboradores", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"mama@test.com\", \"permiso\": \"LECTURA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("No se puede invitar a este usuario"));
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
