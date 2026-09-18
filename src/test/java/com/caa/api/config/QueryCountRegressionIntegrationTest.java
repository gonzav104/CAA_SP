package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.ParadigmaCartilla;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Regresión de conteo de queries para los dos paths N+1 aplanados en este cambio:
 * detalle de cartilla (categorías + items) y "mis pacientes" (FAMILIAR).
 * <p>
 * No lleva {@code @Transactional}: una transacción manejada por el test compartiría el
 * persistence context con la llamada bajo medición y subcontaría los PreparedStatement.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caaquerycount;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("Regresión de conteo de queries — detalle de cartilla y mis-pacientes (FAMILIAR)")
class QueryCountRegressionIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private CartillaRepository cartillaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ItemCartillaRepository itemCartillaRepository;

    @Autowired
    private PacienteFamiliarRepository pacienteFamiliarRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Usuario terapeutaA;
    private Usuario terapeutaB;
    private Paciente pacienteA;
    private Paciente pacienteB;
    private Cartilla cartillaChica;
    private Cartilla cartillaGrande;
    private Cartilla cartillaAjena;

    private Categoria catChicaConItems1;
    private Categoria catChicaConItems2;
    private Categoria catChicaVacia;
    private Set<UUID> categoriaIdsChica;
    private Set<UUID> itemIdsChica;
    private Set<UUID> categoriaIdsAjena;
    private Set<UUID> itemIdsAjena;

    private Usuario familiarUno;
    private Usuario familiarMuchos;
    private PacienteFamiliar vinculoUno;
    private final java.util.Map<UUID, PermisoColaborador> permisosMuchos = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        terapeutaA = crearUsuario("terapeutaA@ejemplo.com", "Terapeuta A", RolUsuario.TERAPEUTA);
        terapeutaB = crearUsuario("terapeutaB@ejemplo.com", "Terapeuta B", RolUsuario.TERAPEUTA);

        pacienteA = crearPaciente(terapeutaA, "Nico", "Perez");
        pacienteB = crearPaciente(terapeutaB, "Ana", "Gomez");

        // cartillaChica: 3 categorías (una vacía), 4 items repartidos en las otras dos.
        // Tanto las categorías como los items se insertan FUERA de su orden declarado,
        // para que el orden de inserción no coincida con el esperado. Si coincidiera,
        // las aserciones de orden pasarían aunque el ORDER BY no existiera.
        cartillaChica = crearCartilla(pacienteA, terapeutaA, "Chica");
        catChicaVacia = crearCategoria(cartillaChica, "Cat vacía", 2);
        catChicaConItems1 = crearCategoria(cartillaChica, "Cat 1", 0);
        catChicaConItems2 = crearCategoria(cartillaChica, "Cat 2", 1);

        ItemCartilla i2 = crearItem(catChicaConItems1, "Item orden 2", 2);
        ItemCartilla i1 = crearItem(catChicaConItems1, "Item orden 1", 1);
        ItemCartilla i0 = crearItem(catChicaConItems1, "Item orden 0", 0);
        ItemCartilla j1 = crearItem(catChicaConItems2, "Item cat2 orden 1", 1);

        categoriaIdsChica = new HashSet<>(java.util.List.of(
                catChicaConItems1.getId(), catChicaConItems2.getId(), catChicaVacia.getId()));
        itemIdsChica = new HashSet<>(java.util.List.of(
                i0.getId(), i1.getId(), i2.getId(), j1.getId()));

        // cartillaGrande: 5 categorías × 10 items = 50 items, mismo paciente/terapeuta.
        cartillaGrande = crearCartilla(pacienteA, terapeutaA, "Grande");
        for (int c = 0; c < 5; c++) {
            Categoria categoria = crearCategoria(cartillaGrande, "CatGrande " + c, c);
            for (int it = 0; it < 10; it++) {
                crearItem(categoria, "ItemGrande " + c + "-" + it, it);
            }
        }

        // cartillaAjena: pertenece a pacienteB, nunca debe aparecer en una respuesta de A.
        cartillaAjena = crearCartilla(pacienteB, terapeutaB, "Ajena");
        Categoria catAjena = crearCategoria(cartillaAjena, "Cat ajena", 0);
        ItemCartilla itemAjeno = crearItem(catAjena, "Item ajeno", 0);
        categoriaIdsAjena = new HashSet<>(java.util.List.of(catAjena.getId()));
        itemIdsAjena = new HashSet<>(java.util.List.of(itemAjeno.getId()));

        // familiarUno: vinculado solo a pacienteA (LECTURA); pacienteB existe y NO está vinculado.
        familiarUno = crearUsuario("familiaruno@ejemplo.com", "Familiar Uno", RolUsuario.FAMILIAR);
        vinculoUno = vincular(pacienteA, familiarUno, PermisoColaborador.LECTURA);

        // familiarMuchos: vinculado a 5 pacientes distintos con permisos mixtos.
        familiarMuchos = crearUsuario("familiarmuchos@ejemplo.com", "Familiar Muchos", RolUsuario.FAMILIAR);
        for (int p = 0; p < 5; p++) {
            Paciente paciente = crearPaciente(terapeutaA, "PacienteMuchos" + p, "Apellido" + p);
            PermisoColaborador permiso = (p % 2 == 0) ? PermisoColaborador.LECTURA : PermisoColaborador.EDICION_LIMITADA;
            vincular(paciente, familiarMuchos, permiso);
            permisosMuchos.put(paciente.getId(), permiso);
        }
    }

    @AfterEach
    void tearDown() {
        // El contexto de Spring (y por lo tanto la base H2) se comparte entre métodos de esta
        // clase; sin limpieza, los fixtures de un test chocarían con los del siguiente
        // (ej. email único de usuarios). Borrado en orden inverso de dependencia de FK.
        itemCartillaRepository.deleteAll();
        categoriaRepository.deleteAll();
        cartillaRepository.deleteAll();
        pacienteFamiliarRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ──────────────────────────────────────────────
    //  Detalle de cartilla
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Conteo de queries constante independiente de N categorías / M items")
    void detalleCartilla_conteoConstante() throws Exception {
        long deltaChica = medirDeltaDetalle(cartillaChica);
        long deltaGrande = medirDeltaDetalle(cartillaGrande);

        assertThat(deltaChica).isEqualTo(deltaGrande);
    }

    @Test
    @DisplayName("No devuelve categorías ni items de otro paciente")
    void detalleCartilla_noDevuelveDatosDeOtroPaciente() throws Exception {
        JsonNode detalle = obtenerDetalle(cartillaChica, terapeutaA);

        Set<UUID> categoriaIdsRespuesta = extraerIds(detalle.get("categorias"));
        Set<UUID> itemIdsRespuesta = new HashSet<>();
        for (JsonNode categoria : detalle.get("categorias")) {
            itemIdsRespuesta.addAll(extraerIds(categoria.get("items")));
        }

        assertThat(categoriaIdsRespuesta).isEqualTo(categoriaIdsChica);
        assertThat(itemIdsRespuesta).isEqualTo(itemIdsChica);
        assertThat(categoriaIdsRespuesta).doesNotContainAnyElementsOf(categoriaIdsAjena);
        assertThat(itemIdsRespuesta).doesNotContainAnyElementsOf(itemIdsAjena);
    }

    @Test
    @DisplayName("Una categoría sin items aparece con items vacío, no se excluye")
    void detalleCartilla_categoriaSinItems_apareceVacia() throws Exception {
        JsonNode detalle = obtenerDetalle(cartillaChica, terapeutaA);

        JsonNode vacia = null;
        for (JsonNode categoria : detalle.get("categorias")) {
            if (categoria.get("id").asText().equals(catChicaVacia.getId().toString())) {
                vacia = categoria;
            }
        }

        assertThat(vacia).isNotNull();
        assertThat(vacia.get("items")).isEmpty();
    }

    @Test
    @DisplayName("Categorías ordenadas por orden ascendente, sin importar el orden de inserción")
    void detalleCartilla_categoriasOrdenadasPorOrden() throws Exception {
        // Las categorías se insertaron como 2, 0, 1 (ver setUp). Si el ORDER BY se
        // perdiera al reescribir el fetch, la respuesta saldría en orden de inserción
        // y esta asercion fallaria. En una cartilla de comunicacion el orden ES la
        // funcionalidad: el paciente aprende donde esta cada categoria.
        JsonNode detalle = obtenerDetalle(cartillaChica, terapeutaA);

        JsonNode categorias = detalle.get("categorias");
        assertThat(categorias).hasSize(3);
        assertThat(categorias.get(0).get("orden").asInt()).isEqualTo(0);
        assertThat(categorias.get(1).get("orden").asInt()).isEqualTo(1);
        assertThat(categorias.get(2).get("orden").asInt()).isEqualTo(2);

        assertThat(categorias.get(0).get("id").asText()).isEqualTo(catChicaConItems1.getId().toString());
        assertThat(categorias.get(1).get("id").asText()).isEqualTo(catChicaConItems2.getId().toString());
        assertThat(categorias.get(2).get("id").asText()).isEqualTo(catChicaVacia.getId().toString());
    }

    @Test
    @DisplayName("Items ordenados por ordenVisual ascendente por categoría, sin importar el orden de inserción")
    void detalleCartilla_itemsOrdenadosPorOrdenVisual() throws Exception {
        JsonNode detalle = obtenerDetalle(cartillaChica, terapeutaA);

        JsonNode cat1 = null;
        for (JsonNode categoria : detalle.get("categorias")) {
            if (categoria.get("id").asText().equals(catChicaConItems1.getId().toString())) {
                cat1 = categoria;
            }
        }

        assertThat(cat1).isNotNull();
        JsonNode items = cat1.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("ordenVisual").asInt()).isEqualTo(0);
        assertThat(items.get(1).get("ordenVisual").asInt()).isEqualTo(1);
        assertThat(items.get(2).get("ordenVisual").asInt()).isEqualTo(2);
    }

    // ──────────────────────────────────────────────
    //  Mis pacientes (FAMILIAR)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Conteo de queries constante independiente de K vínculos")
    void misPacientes_familiar_conteoConstante() throws Exception {
        long deltaUno = medirDeltaMisPacientes(familiarUno);
        long deltaMuchos = medirDeltaMisPacientes(familiarMuchos);

        assertThat(deltaUno).isEqualTo(deltaMuchos);
    }

    @Test
    @DisplayName("Devuelve exactamente el set de pacientes vinculados, sin ampliar el ownership")
    void misPacientes_familiar_devuelveExactamenteSusPacientes() throws Exception {
        JsonNode pacientes = obtenerMisPacientes(familiarUno);

        Set<UUID> idsRespuesta = extraerIds(pacientes);

        assertThat(idsRespuesta).isEqualTo(Set.of(pacienteA.getId()));
    }

    @Test
    @DisplayName("miPermiso coincide con el vínculo almacenado de cada paciente, no con otro")
    void misPacientes_familiar_permisoCoincideConVinculo() throws Exception {
        JsonNode pacientes = obtenerMisPacientes(familiarMuchos);

        assertThat(pacientes).hasSize(5);
        for (JsonNode paciente : pacientes) {
            UUID id = UUID.fromString(paciente.get("id").asText());
            PermisoColaborador esperado = permisosMuchos.get(id);
            assertThat(esperado).isNotNull();
            assertThat(paciente.get("miPermiso").asText()).isEqualTo(esperado.name());
        }
    }

    // ──────────────────────────────────────────────
    //  Helpers de medición
    // ──────────────────────────────────────────────

    private long medirDeltaDetalle(Cartilla cartilla) throws Exception {
        Statistics stats = statistics();
        stats.clear();
        long before = stats.getPrepareStatementCount();

        mockMvc.perform(get("/api/pacientes/{pacienteId}/cartillas/{cartillaId}",
                        cartilla.getPaciente().getId(), cartilla.getId())
                        .header("Authorization", "Bearer " + jwtService.generarToken(terapeutaA)))
                .andExpect(status().isOk());

        return stats.getPrepareStatementCount() - before;
    }

    private long medirDeltaMisPacientes(Usuario familiar) throws Exception {
        Statistics stats = statistics();
        stats.clear();
        long before = stats.getPrepareStatementCount();

        mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer " + jwtService.generarToken(familiar)))
                .andExpect(status().isOk());

        return stats.getPrepareStatementCount() - before;
    }

    private JsonNode obtenerDetalle(Cartilla cartilla, Usuario usuario) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/pacientes/{pacienteId}/cartillas/{cartillaId}",
                        cartilla.getPaciente().getId(), cartilla.getId())
                        .header("Authorization", "Bearer " + jwtService.generarToken(usuario)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private JsonNode obtenerMisPacientes(Usuario familiar) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer " + jwtService.generarToken(familiar)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private Set<UUID> extraerIds(JsonNode array) {
        Set<UUID> ids = new HashSet<>();
        for (JsonNode nodo : array) {
            ids.add(UUID.fromString(nodo.get("id").asText()));
        }
        return ids;
    }

    // ──────────────────────────────────────────────
    //  Helpers de fixtures
    // ──────────────────────────────────────────────

    private Usuario crearUsuario(String email, String nombre, RolUsuario rol) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre(nombre)
                .rol(rol)
                .build());
    }

    private Paciente crearPaciente(Usuario terapeuta, String nombre, String apellido) {
        return pacienteRepository.save(Paciente.builder()
                .terapeuta(terapeuta)
                .nombre(nombre)
                .apellido(apellido)
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build());
    }

    private Cartilla crearCartilla(Paciente paciente, Usuario creador, String nombre) {
        return cartillaRepository.save(Cartilla.builder()
                .paciente(paciente)
                .creador(creador)
                .nombre(nombre)
                .esPrincipal(false)
                .paradigma(ParadigmaCartilla.TAXONOMICA)
                .build());
    }

    private Categoria crearCategoria(Cartilla cartilla, String nombre, int orden) {
        return categoriaRepository.save(Categoria.builder()
                .cartilla(cartilla)
                .nombre(nombre)
                .colorHex("#FF0000")
                .orden(orden)
                .build());
    }

    private ItemCartilla crearItem(Categoria categoria, String texto, int ordenVisual) {
        return itemCartillaRepository.save(ItemCartilla.builder()
                .categoria(categoria)
                .textoHablado(texto)
                .ordenVisual(ordenVisual)
                .build());
    }

    private PacienteFamiliar vincular(Paciente paciente, Usuario familiar, PermisoColaborador permiso) {
        return pacienteFamiliarRepository.save(PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(paciente.getId(), familiar.getId()))
                .paciente(paciente)
                .usuario(familiar)
                .permiso(permiso)
                .build());
    }
}
