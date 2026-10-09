package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Corre las sentencias REALES de las MIGRACIÓN 011-015 (extraídas verbatim de {@code init.sql},
 * no una adaptación H2) contra PostgreSQL real, sobre un dataset representativo previo a la
 * migración. Es la prueba autoritativa de corrección e idempotencia del backfill — a diferencia
 * de {@link MigracionBackfillIntegrationTest} (H2), esta ejecuta la sintaxis PostgreSQL genuina
 * ({@code CREATE LOCAL TEMPORARY TABLE ... AS SELECT}, bloques {@code DO $$}, triggers diferidos)
 * que H2 no puede correr de forma nativa.
 */
@DisplayName("MIGRACIÓN 011-015 (PostgreSQL real): backfill correcto y re-ejecutable sin efecto")
class MigracionBackfillPostgresIntegrationTest extends PostgresTestcontainerBase {

    @Test
    @DisplayName("Backfill correcto en la primera corrida y sin filas adicionales/duplicadas al re-correr")
    void backfillCorrectoYReejecucionSinEfecto() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            String sqlCompleto = Files.readString(Path.of("init.sql"), StandardCharsets.UTF_8);

            // Baseline previo al expand: MIGRACIÓN 001-010 (idéntico a una base en producción hoy).
            ejecutarScript(conexion, sqlCompleto.substring(0, indiceDe(sqlCompleto, "-- MIGRACIÓN 011")));

            UUID ownerA = crearUsuario(conexion, "ownerA@backfill.test");
            UUID terapeutaB = crearUsuario(conexion, "terapeutaB@backfill.test");
            UUID pacienteA1 = crearPaciente(conexion, ownerA, "A1");
            UUID pacienteA2 = crearPaciente(conexion, ownerA, "A2");
            // terapeutaB queda sin pacientes a propósito: no debe recibir organización ni membresía.

            // MIGRACIÓN 011-014 verbatim: tipos, tablas, backfill de organizaciones/OWNER, DDL de
            // pacientes.organizacion_id + pacientes_terapeutas + invitaciones.
            ejecutarScript(conexion, extraerBloque(sqlCompleto, "-- MIGRACIÓN 011", "-- MIGRACIÓN 015"));

            UUID orgA = idOrganizacionDe(conexion, ownerA);

            // Simula una "corrida parcial previa": pacienteA1 ya fue asignado a su organización
            // por código nuevo (p. ej. un OWNER/ADMIN sin esTerapeuta creó el registro de org vía
            // la API), SIN fila pacientes_terapeutas todavía. pacienteA2 queda sin organización,
            // representando el caso normal que el backfill de la MIGRACIÓN 015 debe completar.
            try (Statement statement = conexion.createStatement()) {
                statement.execute("UPDATE pacientes SET organizacion_id = '" + orgA + "' WHERE id = '" + pacienteA1 + "'");
            }

            // MIGRACIÓN 015 verbatim: backfill de pacientes.organizacion_id + pacientes_terapeutas,
            // seguido de la función y los triggers de restricción diferida.
            String migracion015 = extraerBloque(sqlCompleto, "-- MIGRACIÓN 015", null);
            ejecutarScript(conexion, migracion015);

            // --- Primera corrida ---
            assertThat(organizacionIdDe(conexion, pacienteA1)).isEqualTo(orgA);
            assertThat(tieneAsignacion(conexion, pacienteA1, ownerA)).as("pacienteA1 NO debe recibir asignación: "
                    + "ya tenía organización antes de la MIGRACIÓN 015 (excluido de tmp_pacientes_sin_org)").isFalse();

            assertThat(organizacionIdDe(conexion, pacienteA2)).isEqualTo(orgA);
            assertThat(tieneAsignacion(conexion, pacienteA2, ownerA)).as("pacienteA2 SÍ debe recibir asignación: "
                    + "no tenía organización y ownerA es es_terapeuta=TRUE").isTrue();

            assertThat(contarOrganizacionesDe(conexion, terapeutaB)).isZero();
            assertThat(contarOwnersDe(conexion, orgA)).isEqualTo(1);

            long organizacionesAntes = contar(conexion, "organizaciones");
            long membresiasAntes = contar(conexion, "membresias");
            long asignacionesAntes = contar(conexion, "pacientes_terapeutas");

            // --- Re-ejecución verbatim de los mismos bloques (idempotencia) ---
            ejecutarScript(conexion, extraerBloque(sqlCompleto, "-- MIGRACIÓN 013", "-- MIGRACIÓN 014"));
            ejecutarScript(conexion, migracion015);

            assertThat(contar(conexion, "organizaciones")).isEqualTo(organizacionesAntes);
            assertThat(contar(conexion, "membresias")).isEqualTo(membresiasAntes);
            assertThat(contar(conexion, "pacientes_terapeutas")).isEqualTo(asignacionesAntes);
            assertThat(organizacionIdDe(conexion, pacienteA1)).isEqualTo(orgA);
            assertThat(organizacionIdDe(conexion, pacienteA2)).isEqualTo(orgA);
        }
    }

    private int indiceDe(String sql, String marca) {
        int indice = sql.indexOf(marca);
        assertThat(indice).as("init.sql debe contener el marcador " + marca).isNotNegative();
        return indice;
    }

    private String extraerBloque(String sqlCompleto, String marcaInicio, String marcaFinExclusiva) {
        int desde = indiceDe(sqlCompleto, marcaInicio);
        int hasta = marcaFinExclusiva == null ? sqlCompleto.length() : indiceDe(sqlCompleto, marcaFinExclusiva);
        return sqlCompleto.substring(desde, hasta);
    }

    private UUID crearUsuario(Connection conexion, String email) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO usuarios (email, password_hash, nombre, rol) VALUES ('"
                             + email + "', 'hash', 'Test', 'TERAPEUTA') RETURNING id")) {
            resultSet.next();
            return (UUID) resultSet.getObject(1);
        }
    }

    private UUID crearPaciente(Connection conexion, UUID terapeutaId, String nombre) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO pacientes (terapeuta_id, nombre, apellido, fecha_nacimiento) VALUES ('"
                             + terapeutaId + "', '" + nombre + "', 'Perez', '2015-05-10') RETURNING id")) {
            resultSet.next();
            return (UUID) resultSet.getObject(1);
        }
    }

    private UUID idOrganizacionDe(Connection conexion, UUID creadoPorId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id FROM organizaciones WHERE creado_por_id = '" + creadoPorId + "'")) {
            resultSet.next();
            return (UUID) resultSet.getObject(1);
        }
    }

    private UUID organizacionIdDe(Connection conexion, UUID pacienteId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT organizacion_id FROM pacientes WHERE id = '" + pacienteId + "'")) {
            resultSet.next();
            return (UUID) resultSet.getObject(1);
        }
    }

    private boolean tieneAsignacion(Connection conexion, UUID pacienteId, UUID usuarioId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM pacientes_terapeutas WHERE paciente_id = '" + pacienteId
                             + "' AND usuario_id = '" + usuarioId + "'")) {
            resultSet.next();
            return resultSet.getLong(1) > 0;
        }
    }

    private long contarOrganizacionesDe(Connection conexion, UUID creadoPorId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM organizaciones WHERE creado_por_id = '" + creadoPorId + "'")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long contarOwnersDe(Connection conexion, UUID organizacionId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM membresias WHERE organizacion_id = '" + organizacionId
                             + "' AND rol_gestion = 'OWNER'")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long contar(Connection conexion, String tabla) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
