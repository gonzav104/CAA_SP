package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
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
import com.caa.api.services.CartillaService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/**
 * Una sola cartilla principal por paciente, contra repositorios y servicios REALES sobre H2.
 * <p>
 * No lleva {@code @Transactional}: cada llamada al servicio abre y cierra su propia transacción,
 * de modo que los commits y rollbacks son reales. H2 no soporta índices parciales, así que el
 * índice único parcial de PostgreSQL (MIGRACIÓN 010) se emula con una columna generada
 * ({@code principal_key}) y un índice único sobre ella (los NULL son distintos en H2).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caaprincipalunica;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("Cartilla principal única por paciente — integración contra H2 con índice parcial emulado")
class CartillaPrincipalUnicaIntegrationTest {

    private static final String INDICE_EMULADO = "uq_test_una_principal";

    @Autowired private CartillaService cartillaService;
    @Autowired private CartillaRepository cartillaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ItemCartillaRepository itemCartillaRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private PacienteFamiliarRepository pacienteFamiliarRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private Usuario terapeuta;
    private Usuario otroTerapeuta;
    private Usuario familiarEdicion;
    private Usuario familiarLectura;
    private Paciente paciente;
    private Paciente otroPaciente;

    @BeforeEach
    void setUp() {
        // Emulación del índice único parcial (idempotente: el contexto H2 se comparte entre tests)
        jdbc.execute("ALTER TABLE cartillas ADD COLUMN IF NOT EXISTS principal_key UUID "
                + "GENERATED ALWAYS AS (CASE WHEN es_principal THEN paciente_id END)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + INDICE_EMULADO + " ON cartillas(principal_key)");

        terapeuta = crearUsuario("terapeuta@test.com", RolUsuario.TERAPEUTA);
        otroTerapeuta = crearUsuario("otro@test.com", RolUsuario.TERAPEUTA);
        familiarEdicion = crearUsuario("edicion@test.com", RolUsuario.FAMILIAR);
        familiarLectura = crearUsuario("lectura@test.com", RolUsuario.FAMILIAR);

        paciente = crearPaciente(terapeuta, "Nico");
        otroPaciente = crearPaciente(terapeuta, "Ana");
        vincular(paciente, familiarEdicion, PermisoColaborador.EDICION_LIMITADA);
        vincular(paciente, familiarLectura, PermisoColaborador.LECTURA);
    }

    @AfterEach
    void tearDown() {
        // Borrado en orden inverso de dependencia de FK (la base H2 se comparte entre métodos)
        itemCartillaRepository.deleteAll();
        categoriaRepository.deleteAll();
        cartillaRepository.deleteAll();
        pacienteFamiliarRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    @DisplayName("0. Sanidad: el índice parcial emulado RECHAZA una segunda principal del mismo paciente")
    void indiceEmuladoRechazaDuplicados() {
        sembrar(paciente, "A", true, "2024-01-01 10:00:00");

        assertThatThrownBy(() -> sembrar(paciente, "B", true, "2024-01-02 10:00:00"))
                .isInstanceOf(RuntimeException.class);
        // Otro paciente y/o es_principal=false no chocan con el índice
        sembrar(otroPaciente, "C", true, "2024-01-03 10:00:00");
        sembrar(paciente, "D", false, "2024-01-04 10:00:00");
    }

    // ──────────────────────────────────────────────
    //  1. Primera principal
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("1. Crear la primera cartilla principal → queda como única principal")
    void crearPrimeraPrincipal() {
        CartillaResponseDTO creada = crear(terapeuta, paciente, "A", true);

        assertThat(creada.esPrincipal()).isTrue();
        assertThat(principalesDe(paciente)).containsExactly(creada.id());
    }

    // ──────────────────────────────────────────────
    //  2. Crear otra principal desmarca la anterior
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("2. Crear otra principal → la anterior pasa a false y queda exactamente una (la nueva)")
    void crearOtraPrincipalDesmarcaLaAnterior() {
        CartillaResponseDTO primera = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO segunda = crear(terapeuta, paciente, "B", true);

        assertThat(principalesDe(paciente)).containsExactly(segunda.id());
        assertThat(esPrincipal(primera.id())).isFalse();
    }

    // ──────────────────────────────────────────────
    //  3. Convertir una existente en principal
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("3. Convertir una cartilla común en principal con actualizar → la anterior pasa a false")
    void actualizarAPrincipalDesmarcaLaAnterior() {
        CartillaResponseDTO vieja = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO comun = crear(terapeuta, paciente, "B", false);

        CartillaResponseDTO actualizada = actualizar(terapeuta, paciente, comun.id(), "B2", true);

        assertThat(actualizada.esPrincipal()).isTrue();
        assertThat(actualizada.nombre()).isEqualTo("B2");
        assertThat(principalesDe(paciente)).containsExactly(comun.id());
        assertThat(esPrincipal(vieja.id())).isFalse();
    }

    // ──────────────────────────────────────────────
    //  4. Nunca más de una principal persistida
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("4. Secuencia mixta de operaciones → tras cada una hay a lo sumo una principal por paciente")
    void secuenciaMixtaNuncaPersisteMultiplesPrincipales() {
        CartillaResponseDTO a = crear(terapeuta, paciente, "A", true);
        assertMaximoUnaPrincipalPorPaciente();
        CartillaResponseDTO b = crear(terapeuta, paciente, "B", false);
        assertMaximoUnaPrincipalPorPaciente();
        actualizar(terapeuta, paciente, b.id(), "B", true);
        assertMaximoUnaPrincipalPorPaciente();
        CartillaResponseDTO c = crear(terapeuta, paciente, "C", true);
        assertMaximoUnaPrincipalPorPaciente();
        actualizar(terapeuta, paciente, a.id(), "A", true);
        assertMaximoUnaPrincipalPorPaciente();
        actualizar(terapeuta, paciente, a.id(), "A", false);
        assertMaximoUnaPrincipalPorPaciente();
        cartillaService.eliminarCartilla(paciente.getId(), c.id(), terapeuta.getEmail());
        assertMaximoUnaPrincipalPorPaciente();
        crear(terapeuta, paciente, "D", true);
        assertMaximoUnaPrincipalPorPaciente();

        assertThat(principalesDe(paciente)).hasSize(1);
    }

    // ──────────────────────────────────────────────
    //  5. Rollback ante falla posterior al desmarcado
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("5a. Crear principal que falla al insertar (nombre demasiado largo) → rollback: la anterior sigue principal")
    void crearConFallaHaceRollbackDelDesmarcado() {
        CartillaResponseDTO anterior = crear(terapeuta, paciente, "A", true);
        String nombreExcedido = "x".repeat(101);

        assertThatThrownBy(() -> crear(terapeuta, paciente, nombreExcedido, true))
                .isInstanceOf(RuntimeException.class);

        assertThat(principalesDe(paciente)).containsExactly(anterior.id());
        assertThat(cartillaRepository.findByPacienteId(paciente.getId())).hasSize(1);
    }

    @Test
    @DisplayName("5b. Actualizar a principal que falla al hacer flush (nombre demasiado largo) → rollback: la anterior sigue principal")
    void actualizarConFallaHaceRollbackDelDesmarcado() {
        CartillaResponseDTO anterior = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO comun = crear(terapeuta, paciente, "B", false);
        String nombreExcedido = "x".repeat(101);

        assertThatThrownBy(() -> actualizar(terapeuta, paciente, comun.id(), nombreExcedido, true))
                .isInstanceOf(RuntimeException.class);

        assertThat(principalesDe(paciente)).containsExactly(anterior.id());
        assertThat(esPrincipal(comun.id())).isFalse();
    }

    // ──────────────────────────────────────────────
    //  6. Familiar con EDICION_LIMITADA
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("6a. Familiar EDICION_LIMITADA crea con esPrincipal=true → AccesoDenegadoException y nada cambia")
    void familiarNoPuedeCrearPrincipal() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A", true);

        assertThatThrownBy(() -> crear(familiarEdicion, paciente, "F", true))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("Solo el terapeuta responsable");

        assertThat(principalesDe(paciente)).containsExactly(principal.id());
        assertThat(cartillaRepository.findByPacienteId(paciente.getId())).hasSize(1);
    }

    @Test
    @DisplayName("6b. Familiar EDICION_LIMITADA cambia esPrincipal de SU cartilla → AccesoDenegadoException; sin cambio sí puede renombrar")
    void familiarNoPuedeCambiarPrincipalPeroSiRenombrar() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO propia = crear(familiarEdicion, paciente, "F", false);

        assertThatThrownBy(() -> actualizar(familiarEdicion, paciente, propia.id(), "F", true))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(principalesDe(paciente)).containsExactly(principal.id());
        assertThat(esPrincipal(propia.id())).isFalse();

        // Sin cambiar el valor (false sobre una común, o sin enviar el campo) sigue permitido
        CartillaResponseDTO renombrada = actualizar(familiarEdicion, paciente, propia.id(), "F2", false);
        assertThat(renombrada.nombre()).isEqualTo("F2");
        CartillaResponseDTO sinCampo = cartillaService.actualizarCartilla(
                paciente.getId(), propia.id(), new CartillaActualizacionDTO("F3", null, null),
                familiarEdicion.getEmail());
        assertThat(sinCampo.nombre()).isEqualTo("F3");
        assertThat(principalesDe(paciente)).containsExactly(principal.id());
    }

    @Test
    @DisplayName("6c. Familiar EDICION_LIMITADA crea su cartilla SIN principal → sigue funcionando")
    void familiarPuedeCrearCartillaComun() {
        CartillaResponseDTO propia = crear(familiarEdicion, paciente, "F", false);
        CartillaResponseDTO sinCampo = cartillaService.crearCartilla(
                paciente.getId(), new CartillaRegistroDTO("G", null, null), familiarEdicion.getEmail());

        assertThat(propia.creadorId()).isEqualTo(familiarEdicion.getId());
        assertThat(sinCampo.esPrincipal()).isFalse();
        assertThat(principalesDe(paciente)).isEmpty();
    }

    @Test
    @DisplayName("6d. Familiar LECTURA crea cartilla → RecursoNoEncontradoException (404) y no AccesoDenegado")
    void familiarLecturaSigueRecibiendo404() {
        assertThatThrownBy(() -> crear(familiarLectura, paciente, "X", true))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(cartillaRepository.findByPacienteId(paciente.getId())).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  7. Terapeuta responsable vs. ajeno
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("7a. El terapeuta responsable establece la principal tanto al crear como al actualizar")
    void terapeutaResponsableEstableceLaPrincipal() {
        CartillaResponseDTO creada = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO comun = crear(terapeuta, paciente, "B", false);
        actualizar(terapeuta, paciente, comun.id(), "B", true);

        assertThat(principalesDe(paciente)).containsExactly(comun.id());
        assertThat(esPrincipal(creada.id())).isFalse();
    }

    @Test
    @DisplayName("7b. Un terapeuta que NO es el dueño del paciente → 404 existente al crear y al actualizar")
    void terapeutaAjenoRecibe404() {
        CartillaResponseDTO propia = crear(terapeuta, paciente, "A", true);

        assertThatThrownBy(() -> crear(otroTerapeuta, paciente, "X", true))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> actualizar(otroTerapeuta, paciente, propia.id(), "X", true))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(principalesDe(paciente)).containsExactly(propia.id());
        assertThat(cartillaRepository.findByPacienteId(paciente.getId())).hasSize(1);
    }

    // ──────────────────────────────────────────────
    //  8. esPrincipal=false no toca a las demás
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("8a. Crear o actualizar con esPrincipal=false nunca modifica otras cartillas")
    void esPrincipalFalseNoTocaOtras() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO comun = crear(terapeuta, paciente, "B", false);
        actualizar(terapeuta, paciente, comun.id(), "B2", false);
        crear(terapeuta, paciente, "C", false);

        assertThat(principalesDe(paciente)).containsExactly(principal.id());
    }

    @Test
    @DisplayName("8b. Reenviar esPrincipal=true sobre la principal actual es un no-op inofensivo")
    void reenviarTrueSobreLaPrincipalEsNoOp() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A", true);

        CartillaResponseDTO r = actualizar(terapeuta, paciente, principal.id(), "A2", true);

        assertThat(r.esPrincipal()).isTrue();
        assertThat(r.nombre()).isEqualTo("A2");
        assertThat(principalesDe(paciente)).containsExactly(principal.id());
    }

    @Test
    @DisplayName("8c. El terapeuta puede quitar la principal actual (esPrincipal=false) y queda en 0 sin promover otra")
    void quitarLaPrincipalDejaCero() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A", true);
        crear(terapeuta, paciente, "B", false);

        actualizar(terapeuta, paciente, principal.id(), "A", false);

        assertThat(principalesDe(paciente)).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  9. Otros pacientes intactos
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("9. Las principales de OTROS pacientes nunca se tocan")
    void principalesDeOtrosPacientesNoSeTocan() {
        CartillaResponseDTO ajena = crear(terapeuta, otroPaciente, "Ajena", true);

        CartillaResponseDTO a = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO b = crear(terapeuta, paciente, "B", false);
        actualizar(terapeuta, paciente, b.id(), "B", true);
        crear(terapeuta, paciente, "C", true);
        cartillaService.eliminarCartilla(paciente.getId(), a.id(), terapeuta.getEmail());

        assertThat(principalesDe(otroPaciente)).containsExactly(ajena.id());
        assertThat(principalesDe(paciente)).hasSize(1);
    }

    // ──────────────────────────────────────────────
    //  10. Eliminar la principal
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("10. Eliminar la principal deja 0 principales y NO promueve otra cartilla")
    void eliminarLaPrincipalNoPromueveOtra() {
        CartillaResponseDTO principal = crear(terapeuta, paciente, "A", true);
        CartillaResponseDTO otra = crear(terapeuta, paciente, "B", false);

        cartillaService.eliminarCartilla(paciente.getId(), principal.id(), terapeuta.getEmail());

        assertThat(principalesDe(paciente)).isEmpty();
        assertThat(esPrincipal(otra.id())).isFalse();
        assertThat(cartillaRepository.findByPacienteId(paciente.getId())).hasSize(1);
    }

    // ──────────────────────────────────────────────
    //  11. MIGRACIÓN 010 (init.sql)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("11a. MIGRACIÓN 010: la normalización conserva la principal MÁS ANTIGUA por paciente y es idempotente")
    void migracionNormalizaDuplicadosConservandoLaMasAntigua() throws IOException {
        // Sin índice emulado para poder sembrar duplicados, como en una base previa a la migración
        jdbc.execute("DROP INDEX IF EXISTS " + INDICE_EMULADO);

        Paciente conTres = crearPaciente(terapeuta, "Tres");
        Paciente conDos = crearPaciente(terapeuta, "Dos");
        Paciente conUna = crearPaciente(terapeuta, "Una");
        Paciente sinPrincipal = crearPaciente(terapeuta, "Cero");

        // Se insertan fuera del orden cronológico para que el orden de alta no coincida con el esperado
        UUID tresMedia = sembrar(conTres, "3-media", true, "2024-02-01 10:00:00");
        UUID tresVieja = sembrar(conTres, "3-vieja", true, "2024-01-01 10:00:00");
        UUID tresNueva = sembrar(conTres, "3-nueva", true, "2024-03-01 10:00:00");
        UUID tresComun = sembrar(conTres, "3-comun", false, "2023-01-01 10:00:00");
        UUID dosNueva = sembrar(conDos, "2-nueva", true, "2024-05-01 10:00:00");
        UUID dosVieja = sembrar(conDos, "2-vieja", true, "2024-04-01 10:00:00");
        UUID unica = sembrar(conUna, "1-unica", true, "2024-06-01 10:00:00");
        UUID cero = sembrar(sinPrincipal, "0-comun", false, "2024-07-01 10:00:00");

        String normalizacion = extraerNormalizacionMigracion010();

        jdbc.execute(normalizacion);

        assertThat(esPrincipal(tresVieja)).isTrue();
        assertThat(esPrincipal(tresMedia)).isFalse();
        assertThat(esPrincipal(tresNueva)).isFalse();
        assertThat(esPrincipal(tresComun)).isFalse();
        assertThat(esPrincipal(dosVieja)).isTrue();
        assertThat(esPrincipal(dosNueva)).isFalse();
        assertThat(esPrincipal(unica)).isTrue();
        assertThat(esPrincipal(cero)).isFalse();

        List<Boolean> despuesDeLaPrimera = snapshotEsPrincipal();

        // Re-ejecutar no cambia nada
        jdbc.execute(normalizacion);
        assertThat(snapshotEsPrincipal()).isEqualTo(despuesDeLaPrimera);
    }

    @Test
    @DisplayName("11b. MIGRACIÓN 010 contiene el CREATE UNIQUE INDEX IF NOT EXISTS parcial, DESPUÉS de la normalización")
    void migracionDeclaraElIndiceUnicoParcial() throws IOException {
        String seccion = seccionMigracion010();

        assertThat(seccion).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_cartillas_una_principal_por_paciente");
        assertThat(seccion).contains("ON cartillas (paciente_id) WHERE es_principal");
        assertThat(seccion.indexOf("UPDATE cartillas"))
                .isNotNegative()
                .isLessThan(seccion.indexOf("CREATE UNIQUE INDEX"));
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private CartillaResponseDTO crear(Usuario usuario, Paciente destino, String nombre, boolean principal) {
        return cartillaService.crearCartilla(
                destino.getId(), new CartillaRegistroDTO(nombre, principal, null), usuario.getEmail());
    }

    private CartillaResponseDTO actualizar(Usuario usuario, Paciente destino, UUID cartillaId,
                                           String nombre, boolean principal) {
        return cartillaService.actualizarCartilla(
                destino.getId(), cartillaId, new CartillaActualizacionDTO(nombre, principal, null),
                usuario.getEmail());
    }

    private List<UUID> principalesDe(Paciente p) {
        return jdbc.queryForList(
                "SELECT id FROM cartillas WHERE paciente_id = ? AND es_principal", UUID.class, p.getId());
    }

    private boolean esPrincipal(UUID cartillaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT es_principal FROM cartillas WHERE id = ?", Boolean.class, cartillaId));
    }

    private void assertMaximoUnaPrincipalPorPaciente() {
        Integer maximo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(n), 0) FROM (SELECT COUNT(*) AS n FROM cartillas "
                        + "WHERE es_principal GROUP BY paciente_id) t", Integer.class);
        assertThat(maximo).isLessThanOrEqualTo(1);
    }

    private List<Boolean> snapshotEsPrincipal() {
        return jdbc.queryForList("SELECT es_principal FROM cartillas ORDER BY nombre", Boolean.class);
    }

    /** Siembra una cartilla directo en la base fijando creado_en (el servicio no lo permite). */
    private UUID sembrar(Paciente destino, String nombre, boolean principal, String creadoEn) {
        Cartilla c = cartillaRepository.save(Cartilla.builder()
                .paciente(destino)
                .creador(terapeuta)
                .nombre(nombre)
                .esPrincipal(principal)
                .paradigma(ParadigmaCartilla.TAXONOMICA)
                .build());
        jdbc.update("UPDATE cartillas SET creado_en = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(creadoEn), c.getId());
        return c.getId();
    }

    private String seccionMigracion010() throws IOException {
        String sql = Files.readString(Path.of("init.sql"), StandardCharsets.UTF_8);
        int inicio = sql.indexOf("MIGRACIÓN 010");
        assertThat(inicio).as("init.sql debe contener la MIGRACIÓN 010").isNotNegative();
        return sql.substring(inicio);
    }

    private String extraerNormalizacionMigracion010() throws IOException {
        String seccion = seccionMigracion010();
        int desde = seccion.indexOf("UPDATE cartillas");
        assertThat(desde).as("la MIGRACIÓN 010 debe contener la normalización").isNotNegative();
        int hasta = seccion.indexOf(';', desde);
        assertThat(hasta).isGreaterThan(desde);
        return seccion.substring(desde, hasta);
    }

    private Usuario crearUsuario(String email, RolUsuario rol) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre(email)
                .rol(rol)
                .build());
    }

    private Paciente crearPaciente(Usuario duenio, String nombre) {
        return pacienteRepository.save(Paciente.builder()
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
