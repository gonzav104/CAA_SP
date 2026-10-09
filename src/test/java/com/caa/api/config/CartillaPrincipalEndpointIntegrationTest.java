package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.CartillaService;
import com.caa.api.services.JwtService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * PUT /api/pacientes/{pacienteId}/cartillas/{cartillaId}/principal contra repositorios y servicios
 * REALES sobre H2 (sin {@code @Transactional}: commits y rollbacks reales). El índice único parcial de
 * PostgreSQL (MIGRACIÓN 010) se emula con una columna generada y un índice único sobre ella.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caaprincipalendpoint;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("Establecer cartilla principal (PUT /principal) — integración contra H2 con índice parcial emulado")
class CartillaPrincipalEndpointIntegrationTest {

    @Autowired private CartillaService cartillaService;
    @Autowired private CartillaRepository cartillaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ItemCartillaRepository itemCartillaRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private PacienteFamiliarRepository pacienteFamiliarRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private OrganizacionRepository organizacionRepository;
    @Autowired private MembresiaRepository membresiaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private Usuario terapeuta;
    private Usuario otroTerapeuta;
    private Usuario familiarEdicion;
    private Usuario familiarLectura;
    private Usuario familiarSinVinculo;
    private Paciente paciente;
    private Paciente otroPaciente;

    @BeforeEach
    void setUp() {
        jdbc.execute("ALTER TABLE cartillas ADD COLUMN IF NOT EXISTS principal_key UUID "
                + "GENERATED ALWAYS AS (CASE WHEN es_principal THEN paciente_id END)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_test_endpoint_una_principal ON cartillas(principal_key)");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

        terapeuta = crearUsuario("terapeuta@test.com", RolUsuario.TERAPEUTA);
        otroTerapeuta = crearUsuario("otro@test.com", RolUsuario.TERAPEUTA);
        familiarEdicion = crearUsuario("edicion@test.com", RolUsuario.FAMILIAR);
        familiarLectura = crearUsuario("lectura@test.com", RolUsuario.FAMILIAR);
        familiarSinVinculo = crearUsuario("libre@test.com", RolUsuario.FAMILIAR);

        paciente = crearPaciente(terapeuta, "Nico");
        otroPaciente = crearPaciente(terapeuta, "Ana");
        vincular(paciente, familiarEdicion, PermisoColaborador.EDICION_LIMITADA);
        vincular(paciente, familiarLectura, PermisoColaborador.LECTURA);
    }

    @AfterEach
    void tearDown() {
        itemCartillaRepository.deleteAll();
        categoriaRepository.deleteAll();
        cartillaRepository.deleteAll();
        pacienteFamiliarRepository.deleteAll();
        pacienteRepository.deleteAll();
        membresiaRepository.deleteAll();
        organizacionRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ──────────────────────────────────────────────
    //  Servicio
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("1. El terapeuta responsable marca una cartilla que ÉL creó")
    void terapeutaMarcaSuPropiaCartilla() {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A");

        CartillaResponseDTO r = establecer(terapeuta, paciente, a.id());

        assertThat(r.id()).isEqualTo(a.id());
        assertThat(r.esPrincipal()).isTrue();
        assertThat(principalesDe(paciente)).containsExactly(a.id());
    }

    @Test
    @DisplayName("2. El terapeuta marca una cartilla creada por un FAMILIAR (EDICION_LIMITADA); "
            + "renombrar/eliminar ahora derivan del acceso al paciente, no de quién la creó (design-part2 §11.2, R2)")
    void terapeutaMarcaCartillaDeFamiliarYPuedeRenombrarYEliminar() {
        CartillaResponseDTO deFamiliar = crear(familiarEdicion, paciente, "Del familiar");

        CartillaResponseDTO r = establecer(terapeuta, paciente, deFamiliar.id());

        assertThat(r.esPrincipal()).isTrue();
        assertThat(r.creadorId()).isEqualTo(familiarEdicion.getId());
        assertThat(principalesDe(paciente)).containsExactly(deFamiliar.id());

        // R2: acceso deriva del paciente (miembro de equipo), nunca de creador_id — el terapeuta
        // (OWNER de la organización) puede renombrar y eliminar una cartilla que NO creó.
        CartillaResponseDTO renombrada = cartillaService.actualizarCartilla(paciente.getId(), deFamiliar.id(),
                new CartillaActualizacionDTO("Renombrada", null, null), terapeuta.getEmail());
        assertThat(renombrada.nombre()).isEqualTo("Renombrada");
        assertThat(nombreDe(deFamiliar.id())).isEqualTo("Renombrada");

        cartillaService.eliminarCartilla(paciente.getId(), deFamiliar.id(), terapeuta.getEmail());
        assertThat(cartillaRepository.findById(deFamiliar.id())).isEmpty();
        assertThat(principalesDe(paciente)).isEmpty();
    }

    @Test
    @DisplayName("3. Un familiar no puede: EDICION_LIMITADA → 403, LECTURA → 403, sin vínculo → 404; la principal no cambia")
    void familiarNoPuedeEstablecerPrincipal() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A");
        establecer(terapeuta, paciente, principal.id());
        CartillaResponseDTO otra = crear(terapeuta, paciente, "B");

        assertThatThrownBy(() -> establecer(familiarEdicion, paciente, otra.id()))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("Solo un miembro del equipo");
        assertThat(principalesDe(paciente)).containsExactly(principal.id());

        assertThatThrownBy(() -> establecer(familiarLectura, paciente, otra.id()))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(principalesDe(paciente)).containsExactly(principal.id());

        assertThatThrownBy(() -> establecer(familiarSinVinculo, paciente, otra.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(principalesDe(paciente)).containsExactly(principal.id());
    }

    @Test
    @DisplayName("4. Cartilla de OTRO paciente (del mismo terapeuta o de uno ajeno) → 404; terapeuta ajeno → 404; nada cambia")
    void cartillaDeOtroPacienteOTerapeutaAjeno() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A");
        establecer(terapeuta, paciente, principal.id());
        CartillaResponseDTO deOtroPaciente = crear(terapeuta, otroPaciente, "Ajena");

        Paciente pacienteAjeno = crearPaciente(otroTerapeuta, "Ajeno");
        CartillaResponseDTO deTerapeutaAjeno = crear(otroTerapeuta, pacienteAjeno, "Otra");

        assertThatThrownBy(() -> establecer(terapeuta, paciente, deOtroPaciente.id()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Cartilla no encontrada");
        assertThatThrownBy(() -> establecer(terapeuta, paciente, deTerapeutaAjeno.id()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Cartilla no encontrada");
        // Terapeuta ajeno sobre un paciente que no es suyo
        CartillaResponseDTO otra = crear(terapeuta, paciente, "B");
        assertThatThrownBy(() -> establecer(otroTerapeuta, paciente, otra.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(principalesDe(paciente)).containsExactly(principal.id());
        assertThat(principalesDe(otroPaciente)).isEmpty();
        assertThat(principalesDe(pacienteAjeno)).isEmpty();
    }

    @Test
    @DisplayName("5. Cartilla inexistente → 404 y nada cambia")
    void cartillaInexistente() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A");
        establecer(terapeuta, paciente, principal.id());

        assertThatThrownBy(() -> establecer(terapeuta, paciente, UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(principalesDe(paciente)).containsExactly(principal.id());
    }

    @Test
    @DisplayName("6. Llamadas repetidas sobre la que ya es principal → mismo resultado y una sola principal")
    void idempotente() {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A");
        crear(terapeuta, paciente, "B");

        CartillaResponseDTO primera = establecer(terapeuta, paciente, a.id());
        CartillaResponseDTO segunda = establecer(terapeuta, paciente, a.id());
        CartillaResponseDTO tercera = establecer(terapeuta, paciente, a.id());

        assertThat(segunda).isEqualTo(primera);
        assertThat(tercera).isEqualTo(primera);
        assertThat(principalesDe(paciente)).containsExactly(a.id());
    }

    @Test
    @DisplayName("7. Reemplazar la principal deja EXACTAMENTE una (incluso si la anterior la creó otro) y no toca a otros pacientes")
    void reemplazarMantieneUnaSola() {
        CartillaResponseDTO deFamiliar = crear(familiarEdicion, paciente, "F");
        CartillaResponseDTO propia = crear(terapeuta, paciente, "T");
        CartillaResponseDTO ajena = crear(terapeuta, otroPaciente, "Ajena");
        establecer(terapeuta, otroPaciente, ajena.id());

        establecer(terapeuta, paciente, deFamiliar.id());
        assertThat(principalesDe(paciente)).containsExactly(deFamiliar.id());

        establecer(terapeuta, paciente, propia.id());

        assertThat(principalesDe(paciente)).containsExactly(propia.id());
        assertThat(esPrincipal(deFamiliar.id())).isFalse();
        assertThat(principalesDe(otroPaciente)).containsExactly(ajena.id());
    }

    @Test
    @DisplayName("8. Concurrencia: N hilos marcando cartillas DISTINTAS del mismo paciente → nunca queda más de una principal")
    void concurrenciaNuncaDejaDosPrincipales() throws Exception {
        int hilos = 8;
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            ids.add(crear(terapeuta, paciente, "C" + i).id());
        }

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Throwable>> resultados = new ArrayList<>();
        try {
            for (UUID id : ids) {
                resultados.add(pool.submit(() -> {
                    listos.countDown();
                    largada.await();
                    try {
                        establecer(terapeuta, paciente, id);
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
            largada.countDown();

            int exitos = 0;
            for (Future<Throwable> f : resultados) {
                Throwable t = f.get(60, TimeUnit.SECONDS);
                if (t == null) {
                    exitos++;
                } else {
                    // Solo se admite un fallo de integridad/bloqueo (el índice es la última defensa)
                    assertThat(t).as("fallo inesperado: %s", t)
                            .isInstanceOfAny(org.springframework.dao.DataAccessException.class,
                                    org.springframework.transaction.TransactionException.class,
                                    jakarta.persistence.PersistenceException.class);
                }
            }
            assertThat(exitos).as("al menos un hilo debe poder establecer la principal").isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(principalesDe(paciente)).hasSize(1);
        assertMaximoUnaPrincipalPorPaciente();
    }

    @Test
    @DisplayName("9. Paciente con 0 principales puede tener una; eliminar la principal deja 0 sin promover otra")
    void deCeroAUnaYDeUnaACero() {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A");
        crear(terapeuta, paciente, "B");
        assertThat(principalesDe(paciente)).isEmpty();

        establecer(terapeuta, paciente, a.id());
        assertThat(principalesDe(paciente)).containsExactly(a.id());

        cartillaService.eliminarCartilla(paciente.getId(), a.id(), terapeuta.getEmail());

        assertThat(principalesDe(paciente)).isEmpty();
        assertThat(cartillaRepository.findByPacienteId(paciente.getId())).hasSize(1);
    }

    // ──────────────────────────────────────────────
    //  HTTP (MockMvc, sin cuerpo)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("HTTP: 200 con la cartilla (esPrincipal=true) y SIN cuerpo de request; queda persistida")
    void http200SinCuerpo() throws Exception {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A");
        CartillaResponseDTO b = crear(familiarEdicion, paciente, "B");
        establecer(terapeuta, paciente, a.id());

        mockMvc.perform(put(ruta(paciente, b.id())).header("Authorization", bearer(terapeuta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(b.id().toString()))
                .andExpect(jsonPath("$.pacienteId").value(paciente.getId().toString()))
                .andExpect(jsonPath("$.nombre").value("B"))
                .andExpect(jsonPath("$.esPrincipal").value(true));

        assertThat(principalesDe(paciente)).containsExactly(b.id());

        // Idempotente por HTTP
        mockMvc.perform(put(ruta(paciente, b.id())).header("Authorization", bearer(terapeuta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esPrincipal").value(true));
        assertThat(principalesDe(paciente)).containsExactly(b.id());
    }

    @Test
    @DisplayName("HTTP: familiar vinculado (EDICION_LIMITADA y LECTURA) → 403")
    void http403FamiliarVinculado() throws Exception {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A");

        mockMvc.perform(put(ruta(paciente, a.id())).header("Authorization", bearer(familiarEdicion)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ruta(paciente, a.id())).header("Authorization", bearer(familiarLectura)))
                .andExpect(status().isForbidden());

        assertThat(principalesDe(paciente)).isEmpty();
    }

    @Test
    @DisplayName("HTTP: cartilla inexistente, de otro paciente, paciente ajeno o familiar sin vínculo → 404")
    void http404() throws Exception {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A");
        CartillaResponseDTO deOtro = crear(terapeuta, otroPaciente, "Z");

        mockMvc.perform(put(ruta(paciente, UUID.randomUUID())).header("Authorization", bearer(terapeuta)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ruta(paciente, deOtro.id())).header("Authorization", bearer(terapeuta)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ruta(paciente, a.id())).header("Authorization", bearer(otroTerapeuta)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ruta(paciente, a.id())).header("Authorization", bearer(familiarSinVinculo)))
                .andExpect(status().isNotFound());

        assertThat(principalesDe(paciente)).isEmpty();
        assertThat(principalesDe(otroPaciente)).isEmpty();
    }

    @Test
    @DisplayName("HTTP: sin token → 401")
    void http401SinToken() throws Exception {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A");

        mockMvc.perform(put(ruta(paciente, a.id())))
                .andExpect(status().isUnauthorized());

        assertThat(principalesDe(paciente)).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private String ruta(Paciente p, UUID cartillaId) {
        return "/api/pacientes/" + p.getId() + "/cartillas/" + cartillaId + "/principal";
    }

    private String bearer(Usuario u) {
        return "Bearer " + jwtService.generarToken(u);
    }

    private CartillaResponseDTO crear(Usuario usuario, Paciente destino, String nombre) {
        return cartillaService.crearCartilla(
                destino.getId(), new CartillaRegistroDTO(nombre, false, null), usuario.getEmail());
    }

    private CartillaResponseDTO establecer(Usuario usuario, Paciente destino, UUID cartillaId) {
        return cartillaService.establecerCartillaPrincipal(destino.getId(), cartillaId, usuario.getEmail());
    }

    private List<UUID> principalesDe(Paciente p) {
        return jdbc.queryForList(
                "SELECT id FROM cartillas WHERE paciente_id = ? AND es_principal", UUID.class, p.getId());
    }

    private boolean esPrincipal(UUID cartillaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT es_principal FROM cartillas WHERE id = ?", Boolean.class, cartillaId));
    }

    private String nombreDe(UUID cartillaId) {
        return jdbc.queryForObject("SELECT nombre FROM cartillas WHERE id = ?", String.class, cartillaId);
    }

    private void assertMaximoUnaPrincipalPorPaciente() {
        Integer maximo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(n), 0) FROM (SELECT COUNT(*) AS n FROM cartillas "
                        + "WHERE es_principal GROUP BY paciente_id) t", Integer.class);
        assertThat(maximo).isLessThanOrEqualTo(1);
    }

    private Usuario crearUsuario(String email, RolUsuario rol) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre(email)
                .rol(rol)
                .build());
    }

    /**
     * Crea el paciente dentro de una organización NUEVA cuyo {@code duenio} es OWNER
     * (esTerapeuta=true), para que el acceso vía {@code AccesoService} (tarea 3.3) resuelva
     * acceso de equipo igual que antes lo hacía {@code findByIdAndTerapeutaId}.
     */
    private Paciente crearPaciente(Usuario duenio, String nombre) {
        Organizacion organizacion = organizacionRepository.save(Organizacion.builder()
                .nombre(nombre + " Org")
                .creadoPor(duenio)
                .build());
        membresiaRepository.save(Membresia.builder()
                .id(new MembresiaId(organizacion.getId(), duenio.getId()))
                .organizacion(organizacion)
                .usuario(duenio)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build());
        return pacienteRepository.save(Paciente.builder()
                .organizacion(organizacion)
                .terapeuta(duenio)
                .nombre(nombre)
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build());
    }

    private void vincular(Paciente destino, Usuario familiar, PermisoColaborador permiso) {
        pacienteFamiliarRepository.save(PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(destino.getId(), familiar.getId()))
                .paciente(destino)
                .usuario(familiar)
                .permiso(permiso)
                .build());
    }
}
