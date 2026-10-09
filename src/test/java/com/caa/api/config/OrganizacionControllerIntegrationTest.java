package com.caa.api.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.RolGestion;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
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
 * {@code OrganizacionController} vía HTTP, con {@code OrganizacionServiceImpl} y
 * {@code AccesoServiceImpl} reales — solo los repositorios se mockean (mismo idiom que
 * {@code CartillaOwnershipIntegrationTest}).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caaorganizacion;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("OrganizacionController — CRUD de organizaciones (HTTP)")
class OrganizacionControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private OrganizacionRepository organizacionRepository;
    @MockitoBean private MembresiaRepository membresiaRepository;
    @MockitoBean private PacienteRepository pacienteRepository;
    @MockitoBean private PacienteTerapeutaRepository pacienteTerapeutaRepository;

    private MockMvc mockMvc;
    private UUID organizacionId;
    private Usuario owner;
    private Usuario miembro;
    private Organizacion organizacion;
    private String tokenOwner;
    private String tokenMiembro;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        organizacionId = UUID.randomUUID();

        // RolUsuario legacy: se conserva en el fixture por compatibilidad con otros tests de este
        // archivo, pero ni JwtService.generarToken ni JwtAuthenticationFilter lo leen más (design
        // §15 stage 6, cutover de registro); no tiene efecto sobre la autorización multi-tenant.
        owner = Usuario.builder().id(UUID.randomUUID()).email("owner@test.com").nombre("Owner")
                .rol(RolUsuario.TERAPEUTA).build();
        miembro = Usuario.builder().id(UUID.randomUUID()).email("miembro@test.com").nombre("Miembro")
                .rol(RolUsuario.TERAPEUTA).build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").creadoPor(owner).build();

        tokenOwner = jwtService.generarToken(owner);
        tokenMiembro = jwtService.generarToken(miembro);

        given(usuarioRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));
        given(usuarioRepository.findByEmail("miembro@test.com")).willReturn(Optional.of(miembro));

        Membresia ownerMembresia = Membresia.builder()
                .id(new MembresiaId(organizacionId, owner.getId()))
                .organizacion(organizacion).usuario(owner)
                .rolGestion(RolGestion.OWNER).esTerapeuta(true).build();
        Membresia miembroMembresia = Membresia.builder()
                .id(new MembresiaId(organizacionId, miembro.getId()))
                .organizacion(organizacion).usuario(miembro)
                .rolGestion(RolGestion.MIEMBRO).esTerapeuta(true).build();

        given(membresiaRepository.findById(new MembresiaId(organizacionId, owner.getId())))
                .willReturn(Optional.of(ownerMembresia));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, miembro.getId())))
                .willReturn(Optional.of(miembroMembresia));
    }

    @Test
    @DisplayName("POST /api/organizaciones sin esTerapeuta → 201, miEsTerapeuta=true por default")
    void crear_sinEsTerapeuta_devuelve201ConDefaultTrue() throws Exception {
        given(organizacionRepository.save(any(Organizacion.class))).willAnswer(inv -> {
            Organizacion o = inv.getArgument(0);
            o.setId(organizacionId);
            return o;
        });

        mockMvc.perform(post("/api/organizaciones")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Consultorio de Owner\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Consultorio de Owner"))
                .andExpect(jsonPath("$.miRolGestion").value("OWNER"))
                .andExpect(jsonPath("$.miEsTerapeuta").value(true));
    }

    @Test
    @DisplayName("POST /api/organizaciones sin token → 401")
    void crear_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/organizaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/organizaciones/{id} como miembro → 200")
    void obtener_comoMiembro_devuelve200() throws Exception {
        mockMvc.perform(get("/api/organizaciones/{id}", organizacionId)
                        .header("Authorization", "Bearer " + tokenMiembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miRolGestion").value("MIEMBRO"));
    }

    @Test
    @DisplayName("GET /api/organizaciones/{id} no-miembro → 404 genérico")
    void obtener_noMiembro_devuelve404() throws Exception {
        UUID otraOrgId = UUID.randomUUID();
        given(membresiaRepository.findById(new MembresiaId(otraOrgId, miembro.getId())))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/organizaciones/{id}", otraOrgId)
                        .header("Authorization", "Bearer " + tokenMiembro))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/organizaciones/{id} como MIEMBRO (sin gestión) → 403")
    void renombrar_comoMiembroSinGestion_devuelve403() throws Exception {
        mockMvc.perform(put("/api/organizaciones/{id}", organizacionId)
                        .header("Authorization", "Bearer " + tokenMiembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo nombre\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/organizaciones/{id} como OWNER → 200, nombre actualizado")
    void renombrar_comoOwner_devuelve200() throws Exception {
        given(organizacionRepository.save(any(Organizacion.class))).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/organizaciones/{id}", organizacionId)
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo nombre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nuevo nombre"));
    }

    @Test
    @DisplayName("DELETE /api/organizaciones/{id} con pacientes existentes → 409, no elimina")
    void eliminar_conPacientes_devuelve409() throws Exception {
        given(pacienteRepository.existsByOrganizacion_Id(organizacionId)).willReturn(true);

        mockMvc.perform(delete("/api/organizaciones/{id}", organizacionId)
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isConflict());

        verify(organizacionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("DELETE /api/organizaciones/{id} sin pacientes → 204")
    void eliminar_sinPacientes_devuelve204() throws Exception {
        given(pacienteRepository.existsByOrganizacion_Id(organizacionId)).willReturn(false);

        mockMvc.perform(delete("/api/organizaciones/{id}", organizacionId)
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isNoContent());

        verify(organizacionRepository).deleteById(organizacionId);
    }

    @Test
    @DisplayName("DELETE /api/organizaciones/{id} como MIEMBRO (no OWNER) → 403")
    void eliminar_comoMiembro_devuelve403() throws Exception {
        mockMvc.perform(delete("/api/organizaciones/{id}", organizacionId)
                        .header("Authorization", "Bearer " + tokenMiembro))
                .andExpect(status().isForbidden());

        verify(organizacionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("GET /api/organizaciones devuelve la lista de membresías del usuario autenticado")
    void listarMisOrganizaciones_devuelveLista() throws Exception {
        Membresia ownerMembresia = Membresia.builder()
                .id(new MembresiaId(organizacionId, owner.getId()))
                .organizacion(organizacion).usuario(owner)
                .rolGestion(RolGestion.OWNER).esTerapeuta(true).build();
        given(membresiaRepository.findByUsuario_Id(owner.getId())).willReturn(List.of(ownerMembresia));

        mockMvc.perform(get("/api/organizaciones")
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(organizacionId.toString()))
                .andExpect(jsonPath("$[0].miRolGestion").value("OWNER"));
    }
}
