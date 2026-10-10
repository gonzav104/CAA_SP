package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.caa.api.models.Paciente;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Extrae las sentencias de backfill de la MIGRACIÓN 013 directamente de {@code init.sql} y las
 * corre, vía JDBC crudo, contra el esquema H2 que Hibernate genera (ddl-auto=create-drop) a
 * partir de las entidades {@code Organizacion}/{@code Membresia}.
 * <p>
 * H2 no soporta sintaxis específica de PostgreSQL (bloques {@code DO $$}, tipos ENUM nativos,
 * {@code CREATE LOCAL TEMPORARY TABLE ... AS SELECT}, triggers de restricción diferidos); esos
 * aspectos están cubiertos exclusivamente por {@link MigracionBackfillPostgresIntegrationTest}
 * (Testcontainers), que SÍ corre la sintaxis PostgreSQL real. Esta clase es el par RED→GREEN de
 * TDD de las tareas 1.1/1.7 (el resto de los tests *PostgresIntegrationTest de esta fase son
 * verificaciones contra DDL ya escrita, no pares RED/GREEN). El RED de la tarea 1.1 se verificó
 * revirtiendo únicamente {@code init.sql} (sin la MIGRACIÓN 013) y confirmando que los tres tests
 * fallaban por el motivo esperado — el anchor de extracción ausente — antes de restaurar la
 * migración para la corrida en verde.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caamigracionbackfill;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("MIGRACIÓN 013 — backfill de organizaciones/membresias OWNER (H2)")
class MigracionBackfillIntegrationTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    // Nota de secuenciación: las entidades JPA Organizacion/Membresia (tareas 1.8-1.9) se
    // implementaron en el mismo lote que esta clase, así que el esquema H2 de "organizaciones"/
    // "membresias" lo genera Hibernate (ddl-auto=create-drop) a partir de esas entidades — no
    // hace falta DDL manual aquí. El par RED/GREEN sigue siendo genuino: el RED de la tarea 1.1
    // se verificó revirtiendo únicamente init.sql (sin la MIGRACIÓN 013) y confirmando que el
    // test fallaba por el motivo esperado (anchor de extracción ausente), independientemente de
    // qué esquema respalde las tablas.

    @AfterEach
    void tearDown() {
        jdbc.execute("DELETE FROM membresias");
        jdbc.execute("DELETE FROM organizaciones");
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @BeforeEach
    void ajustarDefaultsDeTimestampParaInsertsCrudos() {
        // Hibernate (vía @CreationTimestamp) genera estas columnas como NOT NULL SIN default a
        // nivel de base (el valor lo pone el propio Hibernate al guardar vía JPA). El backfill de
        // la MIGRACIÓN 013/015 se ejecuta aquí vía JDBC crudo —exactamente como correría en
        // producción contra PostgreSQL, donde la columna SÍ tiene DEFAULT CURRENT_TIMESTAMP— así
        // que se agrega el mismo default a nivel de columna en el esquema H2 generado, sin tocar
        // en nada el texto de la migración extraída de init.sql.
        jdbc.execute("ALTER TABLE organizaciones ALTER COLUMN creado_en SET DEFAULT CURRENT_TIMESTAMP");
        jdbc.execute("ALTER TABLE membresias ALTER COLUMN unido_en SET DEFAULT CURRENT_TIMESTAMP");
        // Igual razón que arriba: Hibernate genera el UUID de la PK en memoria antes del INSERT
        // (GenerationType.UUID), no vía default de columna; el backfill crudo necesita el default
        // a nivel de base, exactamente como lo tiene la columna real en PostgreSQL (gen_random_uuid()).
        jdbc.execute("ALTER TABLE organizaciones ALTER COLUMN id SET DEFAULT RANDOM_UUID()");
    }

    @Test
    @DisplayName("Terapeuta con >=1 paciente recibe exactamente una Organizacion + Membresia(OWNER, esTerapeuta=true)")
    void terapeutaConPacientesRecibeOrganizacionYMembresiaOwner() throws IOException {
        Usuario terapeuta = crearTerapeuta("terapeuta@backfill.test");
        crearPaciente(terapeuta, "Nico");

        ejecutarBackfill013();

        assertThat(contarOrganizacionesDe(terapeuta.getId())).isEqualTo(1);
        assertThat(esOwnerYTerapeuta(terapeuta.getId())).isTrue();
    }

    @Test
    @DisplayName("Terapeuta sin pacientes NO recibe Organizacion ni Membresia")
    void terapeutaSinPacientesNoRecibeNada() throws IOException {
        Usuario terapeuta = crearTerapeuta("sinpacientes@backfill.test");

        ejecutarBackfill013();

        assertThat(contarOrganizacionesDe(terapeuta.getId())).isZero();
    }

    @Test
    @DisplayName("Re-ejecutar el backfill de la MIGRACIÓN 013 es idempotente: no inserta filas adicionales")
    void backfill013EsIdempotente() throws IOException {
        Usuario terapeuta = crearTerapeuta("idempotente@backfill.test");
        crearPaciente(terapeuta, "Nico");

        ejecutarBackfill013();
        long organizacionesPrimeraCorrida = contar("organizaciones");
        long membresiasPrimeraCorrida = contar("membresias");

        ejecutarBackfill013();

        assertThat(contar("organizaciones")).isEqualTo(organizacionesPrimeraCorrida);
        assertThat(contar("membresias")).isEqualTo(membresiasPrimeraCorrida);
    }

    // ──────────────────────────────────────────────
    //  Extracción de init.sql
    // ──────────────────────────────────────────────

    private void ejecutarBackfill013() throws IOException {
        for (String sentencia : extraerSentenciasBackfill013()) {
            jdbc.execute(sentencia);
        }
    }

    private List<String> extraerSentenciasBackfill013() throws IOException {
        String sql = Files.readString(Path.of("init.sql"), StandardCharsets.UTF_8);
        int inicio = sql.indexOf("-- MIGRACIÓN 013");
        assertThat(inicio).as("init.sql debe contener la MIGRACIÓN 013").isNotNegative();
        int fin = sql.indexOf("-- MIGRACIÓN 014");
        assertThat(fin).as("init.sql debe contener la MIGRACIÓN 014 (límite de la 013)").isGreaterThan(inicio);
        String seccion = sql.substring(inicio, fin);

        String sinComentarios = Arrays.stream(seccion.split("\n"))
                .filter(linea -> !linea.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));

        List<String> sentencias = new ArrayList<>();
        for (String bruta : sinComentarios.split(";")) {
            String limpia = bruta.trim();
            if (limpia.isEmpty() || limpia.equalsIgnoreCase("BEGIN") || limpia.equalsIgnoreCase("COMMIT")) {
                continue;
            }
            sentencias.add(limpia);
        }
        return sentencias;
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Usuario crearTerapeuta(String email) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre(email)

                .build());
    }

    private void crearPaciente(Usuario terapeuta, String nombre) {
        pacienteRepository.save(Paciente.builder()
                .terapeuta(terapeuta)
                .nombre(nombre)
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build());
    }

    private long contarOrganizacionesDe(UUID creadoPorId) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizaciones WHERE creado_por_id = ?", Long.class, creadoPorId);
        return total == null ? 0 : total;
    }

    private boolean esOwnerYTerapeuta(UUID usuarioId) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM membresias WHERE usuario_id = ? AND rol_gestion = 'OWNER' AND es_terapeuta = TRUE",
                Long.class, usuarioId);
        return total != null && total == 1;
    }

    private long contar(String tabla) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
        return total == null ? 0 : total;
    }
}
