package com.caa.api.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteTerapeuta;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import java.time.LocalDate;
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
 * {@code PacienteTerapeutaController} vía HTTP (design-part2 §12.2, §13; tareas 5.14-5.16).
 * {@code PacienteTerapeutaServiceImpl} y {@code AccesoServiceImpl} reales; solo los repositorios
 * se mockean (mismo idiom que {@code MiembroControllerIntegrationTest}).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caaptterapeutas;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("PacienteTerapeutaController — asignación clínica terapeuta↔paciente (HTTP)")
class PacienteTerapeutaControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private OrganizacionRepository organizacionRepository;
    @MockitoBean private MembresiaRepository membresiaRepository;
    @MockitoBean private PacienteRepository pacienteRepository;
    @MockitoBean private PacienteTerapeutaRepository pacienteTerapeutaRepository;
    @MockitoBean private PacienteFamiliarRepository pacienteFamiliarRepository;

    private MockMvc mockMvc;
    private UUID organizacionId;
    private UUID pacienteId;
    private Usuario owner;
    private Usuario admin;
    private Usuario miembroAsignable;
    private Usuario miembroSinCapacidad;
    private Organizacion organizacion;
    private Paciente paciente;
    private String tokenOwner;
    private String tokenAdmin;
    private String tokenMiembroAsignable;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        organizacionId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();

        owner = Usuario.builder().id(UUID.randomUUID()).email("owner@test.com").nombre("Owner")
                .build();
        admin = Usuario.builder().id(UUID.randomUUID()).email("admin@test.com").nombre("Admin")
                .build();
        miembroAsignable = Usuario.builder().id(UUID.randomUUID()).email("asignable@test.com").nombre("Asignable")
                .build();
        miembroSinCapacidad = Usuario.builder().id(UUID.randomUUID()).email("sincapacidad@test.com")
                .nombre("SinCapacidad").build();

        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").creadoPor(owner).build();
        paciente = Paciente.builder().id(pacienteId).organizacion(organizacion)
                .nombre("Nico").apellido("Perez").fechaNacimiento(LocalDate.of(2020, 5, 10)).build();

        tokenOwner = jwtService.generarToken(owner);
        tokenAdmin = jwtService.generarToken(admin);
        tokenMiembroAsignable = jwtService.generarToken(miembroAsignable);

        given(usuarioRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));
        given(usuarioRepository.findByEmail("admin@test.com")).willReturn(Optional.of(admin));
        given(usuarioRepository.findByEmail("asignable@test.com")).willReturn(Optional.of(miembroAsignable));

        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));

        given(membresiaRepository.findById(new MembresiaId(organizacionId, owner.getId())))
                .willReturn(Optional.of(membresiaDe(owner, RolGestion.OWNER, true)));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, admin.getId())))
                .willReturn(Optional.of(membresiaDe(admin, RolGestion.ADMIN, false)));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, miembroAsignable.getId())))
                .willReturn(Optional.of(membresiaDe(miembroAsignable, RolGestion.MIEMBRO, true)));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, miembroSinCapacidad.getId())))
                .willReturn(Optional.of(membresiaDe(miembroSinCapacidad, RolGestion.MIEMBRO, false)));
    }

    private Membresia membresiaDe(Usuario usuario, RolGestion rolGestion, boolean esTerapeuta) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, usuario.getId()))
                .organizacion(organizacion)
                .usuario(usuario)
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build();
    }

    // ──────────────────────────────────────────────
    //  GET /api/pacientes/{pacienteId}/terapeutas
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("GET: OWNER (acceso de gestión) → 200 con el roster")
    void listarAsignados_ownerConAccesoDeGestion_devuelve200() throws Exception {
        PacienteTerapeuta pt = PacienteTerapeuta.builder()
                .id(new PacienteTerapeutaId(pacienteId, miembroAsignable.getId()))
                .paciente(paciente).usuario(miembroAsignable).organizacionId(organizacionId).build();
        given(pacienteTerapeutaRepository.findByPaciente_Id(pacienteId)).willReturn(List.of(pt));

        mockMvc.perform(get("/api/pacientes/{pacienteId}/terapeutas", pacienteId)
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value(miembroAsignable.getId().toString()));
    }

    @Test
    @DisplayName("GET sin token → 401 (no es público)")
    void listarAsignados_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes/{pacienteId}/terapeutas", pacienteId))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    //  POST /api/pacientes/{pacienteId}/terapeutas
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST: OWNER asigna a un miembro con esTerapeuta=true → 201")
    void asignar_ownerSobreMiembroValido_devuelve201() throws Exception {
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, miembroAsignable.getId());
        given(pacienteTerapeutaRepository.findById(id)).willReturn(Optional.empty());
        given(pacienteTerapeutaRepository.saveAndFlush(any(PacienteTerapeuta.class)))
                .willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/pacientes/{pacienteId}/terapeutas", pacienteId)
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + miembroAsignable.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(miembroAsignable.getId().toString()));
    }

    @Test
    @DisplayName("POST: ADMIN puro (esTerapeuta=false) PUEDE asignar a otro miembro → 201")
    void asignar_adminPuro_devuelve201() throws Exception {
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, miembroAsignable.getId());
        given(pacienteTerapeutaRepository.findById(id)).willReturn(Optional.empty());
        given(pacienteTerapeutaRepository.saveAndFlush(any(PacienteTerapeuta.class)))
                .willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/pacientes/{pacienteId}/terapeutas", pacienteId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + miembroAsignable.getId() + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST: objetivo con esTerapeuta=false → 409")
    void asignar_objetivoSinCapacidadClinica_devuelve409() throws Exception {
        given(usuarioRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));

        mockMvc.perform(post("/api/pacientes/{pacienteId}/terapeutas", pacienteId)
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + miembroSinCapacidad.getId() + "\"}"))
                .andExpect(status().isConflict());

        verify(pacienteTerapeutaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("POST: objetivo no es miembro de la organización del paciente → 404")
    void asignar_objetivoNoEsMiembro_devuelve404() throws Exception {
        UUID ajeno = UUID.randomUUID();
        given(membresiaRepository.findById(new MembresiaId(organizacionId, ajeno))).willReturn(Optional.empty());

        mockMvc.perform(post("/api/pacientes/{pacienteId}/terapeutas", pacienteId)
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + ajeno + "\"}"))
                .andExpect(status().isNotFound());

        verify(pacienteTerapeutaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("POST: MIEMBRO sin rolGestion de gestión no puede asignar (ni a sí mismo) → 404")
    void asignar_miembroSinGestion_devuelve404() throws Exception {
        mockMvc.perform(post("/api/pacientes/{pacienteId}/terapeutas", pacienteId)
                        .header("Authorization", "Bearer " + tokenMiembroAsignable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + miembroAsignable.getId() + "\"}"))
                .andExpect(status().isNotFound());

        verify(pacienteTerapeutaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("POST sin body → 400 (usuarioId obligatorio)")
    void asignar_sinBody_devuelve400() throws Exception {
        mockMvc.perform(post("/api/pacientes/{pacienteId}/terapeutas", pacienteId)
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    //  DELETE /api/pacientes/{pacienteId}/terapeutas/{usuarioId}
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("DELETE: OWNER desasigna → 204 (puede dejar al paciente sin terapeutas)")
    void desasignar_owner_devuelve204() throws Exception {
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, miembroAsignable.getId());
        given(pacienteTerapeutaRepository.existsById(id)).willReturn(true);

        mockMvc.perform(delete("/api/pacientes/{pacienteId}/terapeutas/{usuarioId}",
                        pacienteId, miembroAsignable.getId())
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isNoContent());

        verify(pacienteTerapeutaRepository).deleteById(id);
    }

    @Test
    @DisplayName("DELETE: fila inexistente → 204 idempotente, sin llamar deleteById")
    void desasignar_filaInexistente_devuelve204SinBorrar() throws Exception {
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, miembroAsignable.getId());
        given(pacienteTerapeutaRepository.existsById(id)).willReturn(false);

        mockMvc.perform(delete("/api/pacientes/{pacienteId}/terapeutas/{usuarioId}",
                        pacienteId, miembroAsignable.getId())
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isNoContent());

        verify(pacienteTerapeutaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("DELETE: MIEMBRO sin rolGestion de gestión → 404")
    void desasignar_miembroSinGestion_devuelve404() throws Exception {
        mockMvc.perform(delete("/api/pacientes/{pacienteId}/terapeutas/{usuarioId}",
                        pacienteId, miembroAsignable.getId())
                        .header("Authorization", "Bearer " + tokenMiembroAsignable))
                .andExpect(status().isNotFound());

        verify(pacienteTerapeutaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("DELETE sin token → 401 (no es público)")
    void desasignar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(delete("/api/pacientes/{pacienteId}/terapeutas/{usuarioId}",
                        pacienteId, miembroAsignable.getId()))
                .andExpect(status().isUnauthorized());
    }
}
