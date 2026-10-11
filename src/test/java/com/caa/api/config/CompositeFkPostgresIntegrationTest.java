package com.caa.api.config;

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
 * Verifica los dos FK compuestos de {@code pacientes_terapeutas} (init.sql, MIGRACIÓN 014;
 * design §3.1) contra PostgreSQL real: {@code fk_pt_membresia} y {@code fk_pt_paciente_organizacion}.
 * <p>
 * H2 no aplica el mismo enforcement de FKs compuestos (design §3.1), así que este es el único
 * test automatizado que prueba que una combinación inválida de organización/membresía es
 * rechazada por la base de datos misma, no solo por la capa de aplicación. No es RED/GREEN
 * de TDD: verifica DDL ya escrita (tareas 1.2-1.6).
 */
@DisplayName("MIGRACIÓN 014 (PostgreSQL real): FKs compuestos de pacientes_terapeutas")
class CompositeFkPostgresIntegrationTest extends PostgresTestcontainerBase {

    @Test
    @DisplayName("1. (organizacion_id, usuario_id) que no corresponde a ninguna Membresia → rechazado por fk_pt_membresia")
    void organizacionUsuarioSinMembresiaEsRechazado() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);

            UUID ownerA = crearUsuario(conexion, "ownerA@test.com");
            UUID ownerB = crearUsuario(conexion, "ownerB@test.com");
            UUID orgA = crearOrganizacionConOwner(conexion, ownerA);
            crearOrganizacionConOwner(conexion, ownerB);
            UUID pacienteA = crearPaciente(conexion, ownerA, orgA);

            // organizacion_id = orgA (coincide con el paciente), pero ownerB NUNCA fue miembro de orgA:
            // viola fk_pt_membresia exclusivamente (fk_pt_paciente_organizacion sí se cumple).
            assertThatThrownBy(() -> insertarPacienteTerapeuta(conexion, pacienteA, ownerB, orgA))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_pt_membresia");
        }
    }

    @Test
    @DisplayName("2. (paciente_id, organizacion_id) que no corresponde a pacientes(id, organizacion_id) → rechazado por fk_pt_paciente_organizacion")
    void pacienteDeOtraOrganizacionEsRechazado() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);

            UUID ownerA = crearUsuario(conexion, "ownerA2@test.com");
            UUID ownerB = crearUsuario(conexion, "ownerB2@test.com");
            UUID orgA = crearOrganizacionConOwner(conexion, ownerA);
            UUID orgB = crearOrganizacionConOwner(conexion, ownerB);
            UUID pacienteA = crearPaciente(conexion, ownerA, orgA);

            // (orgB, ownerB) SÍ es una Membresia válida (cumple fk_pt_membresia), pero pacienteA
            // pertenece a orgA, no a orgB: viola fk_pt_paciente_organizacion exclusivamente.
            assertThatThrownBy(() -> insertarPacienteTerapeuta(conexion, pacienteA, ownerB, orgB))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_pt_paciente_organizacion");
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

    private UUID crearOrganizacionConOwner(Connection conexion, UUID ownerId) throws SQLException {
        UUID organizacionId;
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO organizaciones (nombre, creado_por_id) VALUES ('Consultorio Test', '"
                             + ownerId + "') RETURNING id")) {
            resultSet.next();
            organizacionId = (UUID) resultSet.getObject(1);
        }
        try (Statement statement = conexion.createStatement()) {
            statement.execute(
                    "INSERT INTO membresias (organizacion_id, usuario_id, rol_gestion, es_terapeuta) VALUES ('"
                            + organizacionId + "', '" + ownerId + "', 'OWNER', TRUE)");
        }
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
