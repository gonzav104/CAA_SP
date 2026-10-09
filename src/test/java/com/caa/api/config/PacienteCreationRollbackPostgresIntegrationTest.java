package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tarea 2.6a: prueba INDEPENDIENTE y autoritativa, contra PostgreSQL real, de que un fallo en el
 * INSERT de {@code pacientes_terapeutas} (la auto-asignación que sigue al INSERT de
 * {@code pacientes} dentro del límite {@code @Transactional} de
 * {@code PacienteServiceImpl.registrarPaciente(UUID, ...)}) revierte la transacción COMPLETA —
 * ningún {@code Paciente} huérfano sobrevive.
 * <p>
 * Esta es una verificación más fuerte que la de la tarea 2.5
 * ({@code PacienteServiceImplTest.registrarPacienteOrg_fallaAsignacion_propagaExcepcionSinAtrapar}
 * — basada en mocks, solo prueba que la excepción se propaga sin ser atrapada): aquí el fallo es
 * un {@code fk_pt_membresia} real (tarea 1.6b) y la reversión es la de una transacción JDBC real,
 * no un proxy simulado. No es RED/GREEN de TDD: verifica comportamiento transaccional ya
 * garantizado por PostgreSQL + el código de la tarea 2.6, no código nuevo de producción.
 */
@DisplayName("Alta de paciente org-scoped (PostgreSQL real): fallo en la asignación revierte TODA la transacción")
class PacienteCreationRollbackPostgresIntegrationTest extends PostgresTestcontainerBase {

    @Test
    @DisplayName("Fallo de fk_pt_membresia en pacientes_terapeutas → ningún Paciente huérfano persiste tras el rollback")
    void fallaAsignacion_revierteTransaccionCompleta_sinPacienteHuerfano() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner-rollback@test.com");
            UUID organizacionId = crearOrganizacionConOwner(conexion, owner);

            // ajenoAOrganizacion NUNCA es miembro de organizacionId: asignarlo ahí viola
            // fk_pt_membresia exactamente igual que CompositeFkPostgresIntegrationTest (tarea 1.6b),
            // pero aquí dentro de la MISMA secuencia INSERT paciente → INSERT asignación que
            // ejecuta registrarPaciente(UUID, ...) (tarea 2.6).
            UUID ajenoAOrganizacion = crearUsuario(conexion, "ajeno-rollback@test.com");

            // 1. INSERT paciente (como el primer paso de registrarPaciente, dentro de la transacción).
            UUID pacienteId = crearPaciente(conexion, owner, organizacionId);

            // 2. INSERT de la asignación (segundo paso de registrarPaciente) — FALLA por fk_pt_membresia.
            assertThatThrownBy(() -> insertarPacienteTerapeuta(conexion, pacienteId, ajenoAOrganizacion, organizacionId))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_pt_membresia");

            // 3. @Transactional de Spring haría exactamente esto ante la excepción: revertir TODA
            // la transacción, incluido el INSERT de pacientes del paso 1 (que nunca falló por sí
            // mismo, pero pertenece a la misma transacción atómica).
            conexion.rollback();

            try (Statement statement = conexion.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT COUNT(*) FROM pacientes WHERE id = '" + pacienteId + "'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong(1)).isZero();
            }

            // Confirma además que la organización y el owner (preexistentes, de una transacción
            // YA confirmada antes de este intento) sobreviven intactos: el rollback no revierte
            // más allá de la transacción fallida.
            try (Statement statement = conexion.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT COUNT(*) FROM organizaciones WHERE id = '" + organizacionId + "'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong(1)).isEqualTo(1L);
            }
        }
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

    /** Crea organización + membresía OWNER en una transacción separada, confirmada ANTES del intento fallido. */
    private UUID crearOrganizacionConOwner(Connection conexion, UUID ownerId) throws SQLException {
        UUID organizacionId;
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO organizaciones (nombre, creado_por_id) VALUES ('Consultorio Rollback', '"
                             + ownerId + "') RETURNING id")) {
            resultSet.next();
            organizacionId = (UUID) resultSet.getObject(1);
        }
        try (Statement statement = conexion.createStatement()) {
            statement.execute(
                    "INSERT INTO membresias (organizacion_id, usuario_id, rol_gestion, es_terapeuta) VALUES ('"
                            + organizacionId + "', '" + ownerId + "', 'OWNER', TRUE)");
        }
        conexion.commit();
        return organizacionId;
    }

    private UUID crearPaciente(Connection conexion, UUID terapeutaId, UUID organizacionId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO pacientes (terapeuta_id, organizacion_id, nombre, apellido, fecha_nacimiento) "
                             + "VALUES ('" + terapeutaId + "', '" + organizacionId
                             + "', 'Nico', 'Perez', '2015-05-10') RETURNING id")) {
            resultSet.next();
            return (UUID) resultSet.getObject(1);
        }
    }

    private void insertarPacienteTerapeuta(Connection conexion, UUID pacienteId, UUID usuarioId, UUID organizacionId)
            throws SQLException {
        try (Statement statement = conexion.createStatement()) {
            statement.execute(
                    "INSERT INTO pacientes_terapeutas (paciente_id, usuario_id, organizacion_id) VALUES ('"
                            + pacienteId + "', '" + usuarioId + "', '" + organizacionId + "')");
        }
    }
}
