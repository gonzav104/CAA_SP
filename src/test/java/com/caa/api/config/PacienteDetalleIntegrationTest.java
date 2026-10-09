package com.caa.api.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.RolGestion;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.JwtService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestpd;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("PacienteDetalle — GET /api/pacientes/{id}")
class PacienteDetalleIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PacienteRepository pacienteRepository;

    @MockitoBean
    private AccesoService accesoService;

    private MockMvc mockMvc;
    private String tokenValido;
    private UUID pacienteId;
    private UUID terapeutaId;
    private UUID organizacionId;
    private Usuario terapeuta;
    private Paciente paciente;
    private Membresia membresiaOwner;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        pacienteId = UUID.randomUUID();
        terapeutaId = UUID.randomUUID();
        organizacionId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(terapeutaId)
                .email("terapeuta@test.com")
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        Organizacion organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        membresiaOwner = Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeutaId))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build();

        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2020, 5, 10))
                .creadoEn(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();

        tokenValido = jwtService.generarToken(terapeuta);
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER))
                .willReturn(new AccesoPaciente(paciente, membresiaOwner, false, null));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} autenticado como miembro de equipo (gestión) → 200 con datos + organizacionId")
    void getPaciente_tokenValido_devuelve200() throws Exception {
        mockMvc.perform(get("/api/pacientes/{id}", pacienteId)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pacienteId.toString()))
                .andExpect(jsonPath("$.nombre").value("Nico"))
                .andExpect(jsonPath("$.apellido").value("Perez"))
                .andExpect(jsonPath("$.fechaNacimiento").value("2020-05-10"))
                .andExpect(jsonPath("$.organizacionId").value(organizacionId.toString()));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} autenticado pero paciente inexistente → 404")
    void getPaciente_pacienteInexistente_devuelve404() throws Exception {
        UUID pacienteInvalido = UUID.randomUUID();
        given(accesoService.exigirCapacidad(pacienteInvalido, terapeuta, Capacidad.LEER))
                .willThrow(new com.caa.api.exceptions.RecursoNoEncontradoException(
                        "Paciente no encontrado o no tiene permisos"));

        mockMvc.perform(get("/api/pacientes/{id}", pacienteInvalido)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} SIN token → 401 (no es público)")
    void getPaciente_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes/{id}", pacienteId))
                .andExpect(status().isUnauthorized());
    }
}