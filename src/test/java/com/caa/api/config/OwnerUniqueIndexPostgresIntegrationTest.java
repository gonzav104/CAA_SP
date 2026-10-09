package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica el índice único PARCIAL {@code uq_membresias_un_owner_por_organizacion}
 * (init.sql, MIGRACIÓN 012; design §3.2/§3.3) contra PostgreSQL real.
 * <p>
 * No es un par RED/GREEN de TDD: es una verificación contra una migración ya escrita
 * (tarea 1.3); debe simplemente pasar una vez que la migración existe. H2 no soporta
 * índices únicos parciales con {@code WHERE}, así que este es el único test automatizado
 * que ejercita el constraint real.
 */
@DisplayName("MIGRACIÓN 012 (PostgreSQL real): a lo sumo un OWNER por organización")
class OwnerUniqueIndexPostgresIntegrationTest extends PostgresTestcontainerBase {

    @Test
    @DisplayName("Insertar una segunda Membresia OWNER para la misma organización es rechazada por el índice único parcial")
    void segundoOwnerEsRechazado() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 013");

            java.util.UUID usuario1 = crearUsuario(conexion, "owner1@test.com");
            java.util.UUID usuario2 = crearUsuario(conexion, "owner2@test.com");
            java.util.UUID organizacion = crearOrganizacion(conexion, usuario1);

            insertarMembresiaOwner(conexion, organizacion, usuario1);

            assertThatThrownBy(() -> insertarMembresiaOwner(conexion, organizacion, usuario2))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_membresias_un_owner_por_organizacion");

            try (Statement statement = conexion.createStatement();
                 var resultSet = statement.executeQuery(
                         "SELECT COUNT(*) FROM membresias WHERE organizacion_id = '" + organizacion
                                 + "' AND rol_gestion = 'OWNER'")) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    private java.util.UUID crearUsuario(Connection conexion, String email) throws SQLException {
        try (Statement statement = conexion.createStatement();
             var resultSet = statement.executeQuery(
                     "INSERT INTO usuarios (email, password_hash, nombre, rol) VALUES ('"
                             + email + "', 'hash', 'Test', 'TERAPEUTA') RETURNING id")) {
            resultSet.next();
            return (java.util.UUID) resultSet.getObject(1);
        }
    }

    private java.util.UUID crearOrganizacion(Connection conexion, java.util.UUID creadoPorId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             var resultSet = statement.executeQuery(
                     "INSERT INTO organizaciones (nombre, creado_por_id) VALUES ('Consultorio Test', '"
                             + creadoPorId + "') RETURNING id")) {
            resultSet.next();
            return (java.util.UUID) resultSet.getObject(1);
        }
    }

    private void insertarMembresiaOwner(Connection conexion, java.util.UUID organizacionId, java.util.UUID usuarioId)
            throws SQLException {
        try (Statement statement = conexion.createStatement()) {
            statement.execute(
                    "INSERT INTO membresias (organizacion_id, usuario_id, rol_gestion, es_terapeuta) VALUES ('"
                            + organizacionId + "', '" + usuarioId + "', 'OWNER', TRUE)");
        }
    }
}
