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
 * {@code MiembroController} vía HTTP (design §12.2): cambio de rolGestion, toggle esTerapeuta,
 * baja de miembro y salida voluntaria. {@code OrganizacionServiceImpl} y {@code AccesoServiceImpl}
 * reales; solo los repositorios se mockean.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caamiembro;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("MiembroController — gestión de membresías (HTTP)")
class MiembroControllerIntegrationTest {

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
    private Usuario admin;
    private Usuario miembro;
    private Organizacion organizacion;
    private String tokenOwner;
    private String tokenAdmin;
    private String tokenMiembro;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        organizacionId = UUID.randomUUID();

        owner = Usuario.builder().id(UUID.randomUUID()).email("owner@test.com").nombre("Owner")
                .build();
        admin = Usuario.builder().id(UUID.randomUUID()).email("admin@test.com").nombre("Admin")
                .build();
        miembro = Usuario.builder().id(UUID.randomUUID()).email("miembro@test.com").nombre("Miembro")
                .build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").creadoPor(owner).build();

        tokenOwner = jwtService.generarToken(owner);
        tokenAdmin = jwtService.generarToken(admin);
        tokenMiembro = jwtService.generarToken(miembro);

        given(usuarioRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));
        given(usuarioRepository.findByEmail("admin@test.com")).willReturn(Optional.of(admin));
        given(usuarioRepository.findByEmail("miembro@test.com")).willReturn(Optional.of(miembro));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));

        Membresia ownerMembresia = membresiaDe(owner, RolGestion.OWNER, true);
        Membresia adminMembresia = membresiaDe(admin, RolGestion.ADMIN, false);
        Membresia miembroMembresia = membresiaDe(miembro, RolGestion.MIEMBRO, true);

        given(membresiaRepository.findById(new MembresiaId(organizacionId, owner.getId())))
                .willReturn(Optional.of(ownerMembresia));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, admin.getId())))
                .willReturn(Optional.of(adminMembresia));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, miembro.getId())))
                .willReturn(Optional.of(miembroMembresia));
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

    @Test
    @DisplayName("GET /miembros cualquier miembro → 200")
    void listarMiembros_cualquierMiembro_devuelve200() throws Exception {
        given(membresiaRepository.findByOrganizacion_Id(organizacionId))
                .willReturn(List.of(membresiaDe(owner, RolGestion.OWNER, true)));

        mockMvc.perform(get("/api/organizaciones/{id}/miembros", organizacionId)
                        .header("Authorization", "Bearer " + tokenMiembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rolGestion").value("OWNER"));
    }

    @Test
    @DisplayName("PUT .../rol-gestion con valor OWNER en el body → 400")
    void cambiarRolGestion_proponeOwner_devuelve400() throws Exception {
        mockMvc.perform(put("/api/organizaciones/{id}/miembros/{usuarioId}/rol-gestion",
                        organizacionId, miembro.getId())
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolGestion\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT .../rol-gestion invocado por ADMIN (no OWNER) → 403")
    void cambiarRolGestion_comoAdmin_devuelve403() throws Exception {
        mockMvc.perform(put("/api/organizaciones/{id}/miembros/{usuarioId}/rol-gestion",
                        organizacionId, miembro.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolGestion\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT .../rol-gestion por OWNER sobre MIEMBRO → 200, ADMIN")
    void cambiarRolGestion_comoOwner_devuelve200() throws Exception {
        given(membresiaRepository.save(any(Membresia.class))).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/organizaciones/{id}/miembros/{usuarioId}/rol-gestion",
                        organizacionId, miembro.getId())
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolGestion\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolGestion").value("ADMIN"));
    }

    @Test
    @DisplayName("PUT .../rol-gestion por OWNER sobre la propia fila OWNER → 403")
    void cambiarRolGestion_targetEsOwner_devuelve403() throws Exception {
        mockMvc.perform(put("/api/organizaciones/{id}/miembros/{usuarioId}/rol-gestion",
                        organizacionId, owner.getId())
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolGestion\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT .../terapeuta por ADMIN sobre el OWNER → 403")
    void cambiarEsTerapeuta_adminSobreOwner_devuelve403() throws Exception {
        mockMvc.perform(put("/api/organizaciones/{id}/miembros/{usuarioId}/terapeuta",
                        organizacionId, owner.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"esTerapeuta\":false}"))
                .andExpect(status().isForbidden());

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT .../terapeuta a false por OWNER → 200 y borra asignaciones del miembro")
    void cambiarEsTerapeuta_aFalse_borraAsignaciones() throws Exception {
        given(membresiaRepository.save(any(Membresia.class))).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/organizaciones/{id}/miembros/{usuarioId}/terapeuta",
                        organizacionId, miembro.getId())
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"esTerapeuta\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esTerapeuta").value(false));

        verify(pacienteTerapeutaRepository).deleteByOrganizacionIdAndUsuario_Id(organizacionId, miembro.getId());
    }

    @Test
    @DisplayName("DELETE /miembros/{id} intentando eliminar al OWNER → 409")
    void eliminarMiembro_targetEsOwner_devuelve409() throws Exception {
        mockMvc.perform(delete("/api/organizaciones/{id}/miembros/{usuarioId}", organizacionId, owner.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());

        verify(membresiaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE /miembros/{id} ADMIN elimina a MIEMBRO → 204")
    void eliminarMiembro_adminEliminaMiembro_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/organizaciones/{id}/miembros/{usuarioId}", organizacionId, miembro.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        verify(pacienteTerapeutaRepository).deleteByOrganizacionIdAndUsuario_Id(organizacionId, miembro.getId());
    }

    @Test
    @DisplayName("DELETE /miembros/{id} salida voluntaria del propio MIEMBRO → 204")
    void eliminarMiembro_salidaVoluntaria_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/organizaciones/{id}/miembros/{usuarioId}", organizacionId, miembro.getId())
                        .header("Authorization", "Bearer " + tokenMiembro))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /transferencia-propiedad por OWNER hacia ADMIN miembro → 204")
    void transferirPropiedad_valido_devuelve204() throws Exception {
        mockMvc.perform(post("/api/organizaciones/{id}/transferencia-propiedad", organizacionId)
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoOwnerUsuarioId\":\"" + admin.getId() + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /transferencia-propiedad invocado por ADMIN (no OWNER) → 403")
    void transferirPropiedad_comoAdmin_devuelve403() throws Exception {
        mockMvc.perform(post("/api/organizaciones/{id}/transferencia-propiedad", organizacionId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoOwnerUsuarioId\":\"" + miembro.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }
}
