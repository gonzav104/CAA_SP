package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PacienteTerapeuta;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Contrato {@code PacienteResponseDTO.capacidades}: capacidades efectivas del usuario autenticado
 * sobre el paciente, idénticas en detalle, listados, POST y PUT, y coherentes con la autorización
 * que el backend sigue aplicando en cada operación.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caacapacidades;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("PacienteResponseDTO.capacidades — OWNER, ADMIN, terapeuta asignado y familiares")
class PacienteCapacidadesIntegrationTest {

    private static final List<String> TODAS = List.of("EDITAR_CONTENIDO", "GESTION_CLINICA", "ADMINISTRAR");
    private static final List<String> CLINICAS = List.of("EDITAR_CONTENIDO", "GESTION_CLINICA");
    private static final List<String> EDICION = List.of("EDITAR_CONTENIDO");
    private static final String BODY_PACIENTE =
            "{\"nombre\":\"Nico\",\"apellido\":\"Perez\",\"fechaNacimiento\":\"2015-05-10\"}";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OrganizacionRepository organizacionRepository;

    @Autowired
    private MembresiaRepository membresiaRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private PacienteTerapeutaRepository pacienteTerapeutaRepository;

    @Autowired
    private PacienteFamiliarRepository pacienteFamiliarRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Organizacion organizacion;
    private Paciente paciente;
    private Usuario owner;
    private Usuario admin;
    private Usuario terapeutaAsignado;
    private Usuario terapeutaNoAsignado;
    private Usuario familiarLectura;
    private Usuario familiarEdicion;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        owner = crearUsuario("owner@ejemplo.com");
        admin = crearUsuario("admin@ejemplo.com");
        terapeutaAsignado = crearUsuario("asignado@ejemplo.com");
        terapeutaNoAsignado = crearUsuario("noasignado@ejemplo.com");
        familiarLectura = crearUsuario("lectura@ejemplo.com");
        familiarEdicion = crearUsuario("edicion@ejemplo.com");

        organizacion = organizacionRepository.save(Organizacion.builder()
                .nombre("Org capacidades")
                .creadoPor(owner)
                .build());
        // OWNER/ADMIN sin esTerapeuta: sus capacidades vienen solo del rol de gestión.
        crearMembresia(owner, RolGestion.OWNER, false);
        crearMembresia(admin, RolGestion.ADMIN, false);
        crearMembresia(terapeutaAsignado, RolGestion.MIEMBRO, true);
        crearMembresia(terapeutaNoAsignado, RolGestion.MIEMBRO, true);

        paciente = pacienteRepository.save(Paciente.builder()
                .organizacion(organizacion)
                .nombre("Ana")
                .apellido("Gomez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build());
        pacienteTerapeutaRepository.save(PacienteTerapeuta.builder()
                .id(new PacienteTerapeutaId(paciente.getId(), terapeutaAsignado.getId()))
                .paciente(paciente)
                .usuario(terapeutaAsignado)
                .organizacionId(organizacion.getId())
                .build());
        vincular(familiarLectura, PermisoColaborador.LECTURA);
        vincular(familiarEdicion, PermisoColaborador.EDICION_LIMITADA);
    }

    @AfterEach
    void tearDown() {
        pacienteTerapeutaRepository.deleteAll();
        pacienteFamiliarRepository.deleteAll();
        pacienteRepository.deleteAll();
        membresiaRepository.deleteAll();
        organizacionRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ──────────────────────────────────────────────
    //  Detalle y listados
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER: todas las capacidades en detalle, mis-pacientes y workspace")
    void owner_todasLasCapacidades() throws Exception {
        assertLecturas(owner, TODAS, null, true);
    }

    @Test
    @DisplayName("ADMIN: todas las capacidades en detalle, mis-pacientes y workspace")
    void admin_todasLasCapacidades() throws Exception {
        assertLecturas(admin, TODAS, null, true);
    }

    @Test
    @DisplayName("Terapeuta asignado: EDITAR_CONTENIDO y GESTION_CLINICA, sin ADMINISTRAR")
    void terapeutaAsignado_capacidadesClinicas() throws Exception {
        assertLecturas(terapeutaAsignado, CLINICAS, null, true);
    }

    @Test
    @DisplayName("Familiar EDICION_LIMITADA: solo EDITAR_CONTENIDO y miPermiso intacto")
    void familiarEdicion_soloEditarContenido() throws Exception {
        assertLecturas(familiarEdicion, EDICION, "EDICION_LIMITADA", false);
    }

    @Test
    @DisplayName("Familiar LECTURA: capacidades vacías y miPermiso intacto")
    void familiarLectura_sinCapacidades() throws Exception {
        assertLecturas(familiarLectura, List.of(), "LECTURA", false);
    }

    @Test
    @DisplayName("Terapeuta no asignado: el paciente no aparece y el detalle sigue siendo 404")
    void terapeutaNoAsignado_sinAcceso() throws Exception {
        perform(get("/api/pacientes/{id}", paciente.getId()), terapeutaNoAsignado, status().isNotFound());
        assertThat(leer(get("/api/pacientes"), terapeutaNoAsignado)).isEmpty();
        assertThat(leer(get("/api/organizaciones/{id}/pacientes", organizacion.getId()), terapeutaNoAsignado))
                .isEmpty();
    }

    // ──────────────────────────────────────────────
    //  PUT / POST
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT devuelve las mismas capacidades y miPermiso que el detalle para cada editor")
    void put_capacidadesCoincidenConDetalle() throws Exception {
        for (Usuario editor : List.of(owner, admin, terapeutaAsignado, familiarEdicion)) {
            JsonNode detalle = leer(get("/api/pacientes/{id}", paciente.getId()), editor);
            JsonNode actualizado = leer(put("/api/pacientes/{id}", paciente.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY_PACIENTE), editor);

            assertThat(capacidades(actualizado)).as(editor.getEmail()).isEqualTo(capacidades(detalle));
            assertThat(actualizado.get("miPermiso")).as(editor.getEmail()).isEqualTo(detalle.get("miPermiso"));
        }
    }

    @Test
    @DisplayName("POST por OWNER sin esTerapeuta: todas las capacidades, coherentes con el detalle posterior")
    void post_owner_todasLasCapacidades() throws Exception {
        JsonNode creado = crearPacienteVia(owner);

        assertThat(capacidades(creado)).isEqualTo(TODAS);
        assertThat(capacidades(leer(get("/api/pacientes/{id}", creado.get("id").asText()), owner)))
                .isEqualTo(TODAS);
    }

    @Test
    @DisplayName("POST por terapeuta MIEMBRO: auto-asignado, capacidades clínicas coherentes con el detalle posterior")
    void post_terapeutaMiembro_capacidadesClinicas() throws Exception {
        JsonNode creado = crearPacienteVia(terapeutaNoAsignado);

        assertThat(capacidades(creado)).isEqualTo(CLINICAS);
        assertThat(capacidades(leer(get("/api/pacientes/{id}", creado.get("id").asText()), terapeutaNoAsignado)))
                .isEqualTo(CLINICAS);
    }

    // ──────────────────────────────────────────────
    //  El backend sigue validando cada operación
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Sin la capacidad anunciada, la operación sigue rechazada con el 404 genérico")
    void operacionesSinCapacidad_siguenRechazadas() throws Exception {
        // EDITAR_CONTENIDO ausente.
        perform(put("/api/pacientes/{id}", paciente.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY_PACIENTE), familiarLectura, status().isNotFound());
        // GESTION_CLINICA ausente.
        perform(get("/api/pacientes/{id}/terapeutas", paciente.getId()), familiarEdicion, status().isNotFound());
        // ADMINISTRAR ausente.
        perform(delete("/api/pacientes/{id}", paciente.getId()), terapeutaAsignado, status().isNotFound());

        assertThat(pacienteRepository.existsById(paciente.getId())).isTrue();
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    /**
     * Detalle, mis-pacientes y (para equipo) workspace de organización devuelven exactamente las
     * mismas capacidades y miPermiso para el usuario.
     */
    private void assertLecturas(Usuario usuario, List<String> esperadas, String miPermiso, boolean equipo)
            throws Exception {
        List<JsonNode> respuestas = new ArrayList<>();
        respuestas.add(leer(get("/api/pacientes/{id}", paciente.getId()), usuario));
        respuestas.add(buscar(leer(get("/api/pacientes"), usuario)));
        if (equipo) {
            respuestas.add(buscar(leer(get("/api/organizaciones/{id}/pacientes", organizacion.getId()), usuario)));
        } else {
            // Familiar sin membresía: el workspace de organización le sigue siendo ajeno.
            perform(get("/api/organizaciones/{id}/pacientes", organizacion.getId()), usuario, status().isNotFound());
        }

        for (JsonNode respuesta : respuestas) {
            assertThat(capacidades(respuesta)).isEqualTo(esperadas);
            if (miPermiso == null) {
                assertThat(respuesta.get("miPermiso").isNull()).isTrue();
            } else {
                assertThat(respuesta.get("miPermiso").asText()).isEqualTo(miPermiso);
            }
        }
    }

    private JsonNode crearPacienteVia(Usuario usuario) throws Exception {
        MvcResult resultado = perform(post("/api/organizaciones/{id}/pacientes", organizacion.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY_PACIENTE), usuario, status().isCreated());
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode buscar(JsonNode lista) {
        for (JsonNode nodo : lista) {
            if (nodo.get("id").asText().equals(paciente.getId().toString())) {
                return nodo;
            }
        }
        throw new AssertionError("El paciente no aparece en la lista: " + lista);
    }

    private List<String> capacidades(JsonNode respuesta) {
        List<String> capacidades = new ArrayList<>();
        respuesta.get("capacidades").forEach(c -> capacidades.add(c.asText()));
        return capacidades;
    }

    private JsonNode leer(MockHttpServletRequestBuilder request, Usuario usuario) throws Exception {
        MvcResult resultado = perform(request, usuario, status().isOk());
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, Usuario usuario, ResultMatcher esperado)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.generarToken(usuario)))
                .andExpect(esperado)
                .andReturn();
    }

    private Usuario crearUsuario(String email) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre(email)
                .build());
    }

    private Membresia crearMembresia(Usuario usuario, RolGestion rolGestion, boolean esTerapeuta) {
        return membresiaRepository.save(Membresia.builder()
                .id(new MembresiaId(organizacion.getId(), usuario.getId()))
                .organizacion(organizacion)
                .usuario(usuario)
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build());
    }

    private PacienteFamiliar vincular(Usuario familiar, PermisoColaborador permiso) {
        return pacienteFamiliarRepository.save(PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(paciente.getId(), familiar.getId()))
                .paciente(paciente)
                .usuario(familiar)
                .permiso(permiso)
                .build());
    }
}
