package com.caa.api.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.JwtService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@code POST/GET /api/organizaciones/{organizacionId}/pacientes} (design §12.2, part 1 §10.3,
 * tareas 2.5/2.6/2.9). El servicio ({@code PacienteServiceImpl}) ya está cubierto en detalle por
 * {@code PacienteServiceImplTest}; este test cubre únicamente el cableado HTTP (rutas, status
 * codes, autenticación) con {@code PacienteService} reemplazado por un mock directo — no se
 * reimplementa la matriz de autorización aquí.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestorgpac;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("OrganizacionPacienteController — POST/GET /api/organizaciones/{organizacionId}/pacientes")
class OrganizacionPacienteControllerIntegrationTest {

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

    @MockitoBean
    private PacienteTerapeutaRepository pacienteTerapeutaRepository;

    private MockMvc mockMvc;
    private String tokenValido;
    private UUID organizacionId;
    private UUID terapeutaId;
    private Usuario terapeuta;
    private Membresia membresiaOwner;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        organizacionId = UUID.randomUUID();
        terapeutaId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(terapeutaId)
                .email("terapeuta@test.com")
                .nombre("Terapeuta")

                .build();

        Organizacion organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        membresiaOwner = Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeutaId))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build();

        tokenValido = jwtService.generarToken(terapeuta);
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
    }

    @Test
    @DisplayName("POST: OWNER/ADMIN con esTerapeuta=true → 201, auto-asignado")
    void postPaciente_ownerEsTerapeuta_devuelve201() throws Exception {
        UUID pacienteId = UUID.randomUUID();
        Organizacion organizacion = membresiaOwner.getOrganizacion();
        Paciente guardado = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2020, 5, 10))
                .creadoEn(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
        given(accesoService.exigirAltaPaciente(organizacionId, terapeuta)).willReturn(membresiaOwner);
        given(pacienteRepository.save(org.mockito.ArgumentMatchers.any(Paciente.class))).willReturn(guardado);

        mockMvc.perform(post("/api/organizaciones/{organizacionId}/pacientes", organizacionId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nico\",\"apellido\":\"Perez\",\"fechaNacimiento\":\"2020-05-10\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Nico"))
                .andExpect(jsonPath("$.organizacionId").value(organizacionId.toString()));
    }

    @Test
    @DisplayName("POST: MIEMBRO con esTerapeuta=false → 403, AccesoService lo rechaza")
    void postPaciente_miembroSinCapacidad_devuelve403() throws Exception {
        given(accesoService.exigirAltaPaciente(organizacionId, terapeuta))
                .willThrow(new AccesoDenegadoException("No tiene permisos para registrar pacientes en esta organización"));

        mockMvc.perform(post("/api/organizaciones/{organizacionId}/pacientes", organizacionId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nico\",\"apellido\":\"Perez\",\"fechaNacimiento\":\"2020-05-10\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST: no-miembro de la organización → 404")
    void postPaciente_noMiembro_devuelve404() throws Exception {
        given(accesoService.exigirAltaPaciente(organizacionId, terapeuta))
                .willThrow(new RecursoNoEncontradoException("Organización no encontrada o no tiene permisos"));

        mockMvc.perform(post("/api/organizaciones/{organizacionId}/pacientes", organizacionId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nico\",\"apellido\":\"Perez\",\"fechaNacimiento\":\"2020-05-10\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST sin token → 401 (no es público)")
    void postPaciente_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/organizaciones/{organizacionId}/pacientes", organizacionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nico\",\"apellido\":\"Perez\",\"fechaNacimiento\":\"2020-05-10\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET: rolGestion OWNER/ADMIN → 200 con el workspace completo")
    void getPacientes_gestion_devuelve200() throws Exception {
        given(accesoService.exigirMembresia(organizacionId, terapeuta)).willReturn(membresiaOwner);
        given(pacienteRepository.findByOrganizacion_Id(organizacionId)).willReturn(List.of());

        mockMvc.perform(get("/api/organizaciones/{organizacionId}/pacientes", organizacionId)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET sin token → 401 (no es público)")
    void getPacientes_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/organizaciones/{organizacionId}/pacientes", organizacionId))
                .andExpect(status().isUnauthorized());
    }
}
