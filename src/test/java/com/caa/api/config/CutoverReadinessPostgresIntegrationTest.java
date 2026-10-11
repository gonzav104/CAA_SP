package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.caa.api.dtos.OrganizacionRegistroDTO;
import com.caa.api.dtos.OrganizacionResponseDTO;
import com.caa.api.services.OrganizacionService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cutover readiness (design-part2 §15 stage 5/6, spec {@code multitenant-data-migration}
 * "Post-Migration Verification"): valida, sobre PostgreSQL real y un dataset representativo
 * con organizaciones y membresías creadas por servicios reales, más pacientes pre-016 sembrados
 * explícitamente con su columna legacy obligatoria, los invariantes de datos de los que depende la
 * MIGRACIÓN 016. Este test NO ejecuta ni rehearsa la MIGRACIÓN 016: solo lee el estado previo.
 * <p>
 * Reutilizable: puede volver a correrse en cualquier momento previo al cutover real, incluido
 * durante el rehearsal manual de la Fase 9 (design-part2 tasks-part3 §9.1), para re-confirmar los
 * mismos invariantes sobre datos frescos.
 * <p>
 * A diferencia del resto de {@code *PostgresIntegrationTest} (SQL crudo, sin contexto de Spring),
 * este test necesita {@code OrganizacionService} para producir membresías representativas. Los
 * pacientes se insertan por JDBC porque el esquema pre-016 todavía exige
 * {@code pacientes.terapeuta_id}, mientras que el modelo Java post-cutover ya no mapea esa columna.
 */
@Testcontainers
@SpringBootTest
@DisplayName("Cutover readiness — invariantes de datos antes de MIGRACIÓN 016 (PostgreSQL real)")
class CutoverReadinessPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void propiedadesDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==");
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("cloudinary.cloud-name", () -> "test-cloud");
        registry.add("cloudinary.api-key", () -> "test-api-key");
        registry.add("cloudinary.api-secret", () -> "test-api-secret");
        registry.add("resend.api-key", () -> "test-resend-api-key");
    }

    /** Aplica solo el bootstrap y las migraciones hasta 015a una sola vez; excluye 016. */
    @BeforeAll
    static void aplicarEsquema() throws Exception {
        String sql = PostgresTestcontainerBase.leerInitSqlHasta("-- MIGRACIÓN 016");
        try (Connection conexion = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = conexion.createStatement()) {
            statement.execute(sql);
        }
    }

    @Autowired
    private OrganizacionService organizacionService;

    @Test
    @DisplayName("Dataset representativo (OWNER clínico auto-asignado, OWNER de gestión sin "
            + "asignar, MIEMBRO asignado por invitación, terapeuta legacy sin pacientes) → cero "
            + "violaciones de los invariantes de cutover")
    void datasetRepresentativo_ceroViolacionesDeInvariantes() throws Exception {
        // 1) OWNER con esTerapeuta=true: crea su propio paciente → auto-asignado (caso típico,
        // design part 1 §10.3).
        String emailOwnerClinico = "owner-clinico@cutover.test";
        UUID ownerClinicoId = crearUsuarioLegacy(emailOwnerClinico, "TERAPEUTA");
        OrganizacionResponseDTO orgClinica = organizacionService.crear(
                new OrganizacionRegistroDTO("Consultorio Clínico", true), emailOwnerClinico);
        try (Connection conexion = abrirConexion()) {
            UUID pacienteId = insertarPacienteLegacy(
                    conexion, orgClinica.id(), ownerClinicoId, "Paciente Asignado");
            insertarPacienteTerapeuta(conexion, pacienteId, ownerClinicoId, orgClinica.id());
        }

        // 2) OWNER con esTerapeuta=false: crea un paciente que queda SIN asignar — caso válido
        // por diseño (design-part2 Risk R11), NO debe ser reportado como violación por este check.
        String emailOwnerGestor = "owner-gestor@cutover.test";
        UUID ownerGestorId = crearUsuarioLegacy(emailOwnerGestor, "TERAPEUTA");
        OrganizacionResponseDTO orgGestora = organizacionService.crear(
                new OrganizacionRegistroDTO("Consultorio Gestor", false), emailOwnerGestor);
        try (Connection conexion = abrirConexion()) {
            insertarPacienteLegacy(conexion, orgGestora.id(), ownerGestorId, "Paciente Sin Asignar");
        }

        // 3) MIEMBRO (no OWNER/ADMIN) con esTerapeuta=true, sumado como si hubiera aceptado una
        // invitación (se inserta la fila final de Membresia directamente — mismo patrón que el
        // resto de los *PostgresIntegrationTest de este proyecto) y asignado a un paciente propio.
        String emailMiembroClinico = "miembro-clinico@cutover.test";
        UUID miembroId = crearUsuarioLegacy(emailMiembroClinico, "TERAPEUTA");
        try (Connection conexion = abrirConexion()) {
            insertarMembresia(conexion, orgGestora.id(), miembroId, "MIEMBRO", true);
            UUID pacienteId = insertarPacienteLegacy(
                    conexion, orgGestora.id(), miembroId, "Paciente De Miembro");
            insertarPacienteTerapeuta(conexion, pacienteId, miembroId, orgGestora.id());
        }

        // 4) Terapeuta legacy sin pacientes y sin organización — caso explícitamente excluido del
        // backfill (spec multitenant-data-migration, "Backfill Scope Limited..."): no debe
        // aparecer como "le falta su workspace" en este check, porque nunca posee un paciente.
        crearUsuarioLegacy("terapeuta-sin-pacientes@cutover.test", "TERAPEUTA");

        try (Connection conexion = abrirConexion()) {
            // Invariante A (spec multitenant-data-migration, "Guarded Four-Step Column Addition
            // for organizacion_id"): ningún paciente sin organizacion_id.
            assertThat(contarEscalar(conexion,
                    "SELECT COUNT(*) FROM pacientes WHERE organizacion_id IS NULL"))
                    .as("pacientes sin organizacion_id").isZero();

            // Invariante B (design-part2 ADR 4): cada organización tiene EXACTAMENTE un OWNER
            // (ni cero, ni más de uno) — lectura de datos, no solo existencia del constraint.
            assertThat(contarEscalar(conexion, """
                    SELECT COUNT(*) FROM (
                        SELECT o.id, COUNT(m.usuario_id) AS owners
                        FROM organizaciones o
                        LEFT JOIN membresias m
                            ON m.organizacion_id = o.id AND m.rol_gestion = 'OWNER'
                        GROUP BY o.id
                        HAVING COUNT(m.usuario_id) <> 1
                    ) violaciones
                    """))
                    .as("organizaciones sin exactamente un OWNER").isZero();

            // Invariante C: ningún Usuario propietario legacy (pacientes.terapeuta_id) de al
            // menos un paciente carece de toda Membresia — "ningún Usuario al que le debería
            // quedar un workspace lo tiene ausente". El terapeuta legacy sin pacientes del caso 4
            // queda fuera a propósito: nunca es terapeuta_id de ningún paciente.
            assertThat(contarEscalar(conexion, """
                    SELECT COUNT(DISTINCT p.terapeuta_id) FROM pacientes p
                    WHERE p.terapeuta_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM membresias m WHERE m.usuario_id = p.terapeuta_id
                      )
                    """))
                    .as("propietarios legacy de pacientes sin ninguna Membresia").isZero();

            // Invariante D: para TODO paciente pre-016, si el dueño legacy (terapeuta_id) tiene
            // esTerapeuta=true en la organización real del paciente, debe existir la fila
            // PacienteTerapeuta
            // correspondiente. Nunca al revés: un dueño con esTerapeuta=false queda sin fila a
            // propósito (caso 2 arriba), por eso el filtro "m.es_terapeuta = TRUE" es necesario
            // para no producir un falso positivo.
            assertThat(contarEscalar(conexion, """
                    SELECT COUNT(*) FROM pacientes p
                    JOIN membresias m
                        ON m.organizacion_id = p.organizacion_id AND m.usuario_id = p.terapeuta_id
                    WHERE m.es_terapeuta = TRUE
                      AND NOT EXISTS (
                          SELECT 1 FROM pacientes_terapeutas pt
                          WHERE pt.paciente_id = p.id AND pt.usuario_id = p.terapeuta_id
                      )
                    """))
                    .as("pacientes cuyo dueño legacy es clínico pero sin fila PacienteTerapeuta").isZero();
        }
    }

    private Connection abrirConexion() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long contarEscalar(Connection conexion, String sql) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /** Usuario "legacy" (con rol todavía poblado, como cualquier usuario preexistente al cutover). */
    private UUID crearUsuarioLegacy(String email, String rolLegacy) throws SQLException {
        try (Connection conexion = abrirConexion();
             Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO usuarios (email, password_hash, nombre, rol) VALUES ('"
                             + email + "', 'hash', 'Test', '" + rolLegacy + "') RETURNING id")) {
            resultSet.next();
            return (UUID) resultSet.getObject(1);
        }
    }

    private void insertarMembresia(Connection conexion, UUID organizacionId, UUID usuarioId,
                                    String rolGestion, boolean esTerapeuta) throws SQLException {
        try (Statement statement = conexion.createStatement()) {
            statement.execute(
                    "INSERT INTO membresias (organizacion_id, usuario_id, rol_gestion, es_terapeuta) VALUES ('"
                            + organizacionId + "', '" + usuarioId + "', '" + rolGestion + "', " + esTerapeuta + ")");
        }
    }

    private UUID insertarPacienteLegacy(Connection conexion, UUID organizacionId,
                                         UUID terapeutaId, String nombre) throws SQLException {
        UUID pacienteId = UUID.randomUUID();
        try (PreparedStatement statement = conexion.prepareStatement("""
                INSERT INTO pacientes
                    (id, terapeuta_id, organizacion_id, nombre, apellido, fecha_nacimiento)
                VALUES (?, ?, ?, ?, 'Apellido', DATE '2015-01-01')
                """)) {
            statement.setObject(1, pacienteId);
            statement.setObject(2, terapeutaId);
            statement.setObject(3, organizacionId);
            statement.setString(4, nombre);
            statement.executeUpdate();
        }
        return pacienteId;
    }

    private void insertarPacienteTerapeuta(Connection conexion, UUID pacienteId,
                                            UUID usuarioId, UUID organizacionId) throws SQLException {
        try (PreparedStatement statement = conexion.prepareStatement("""
                INSERT INTO pacientes_terapeutas (paciente_id, usuario_id, organizacion_id)
                VALUES (?, ?, ?)
                """)) {
            statement.setObject(1, pacienteId);
            statement.setObject(2, usuarioId);
            statement.setObject(3, organizacionId);
            statement.executeUpdate();
        }
    }
}
