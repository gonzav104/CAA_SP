package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica el trigger de restricción DIFERIDO {@code trg_membresias_un_owner}
 * (init.sql, MIGRACIÓN 015; design §3.3) contra PostgreSQL real.
 * <p>
 * H2 no soporta triggers de restricción diferidos en absoluto, así que este es el único
 * test automatizado que prueba su comportamiento. No es RED/GREEN de TDD: verifica DDL
 * ya escrita (tareas 1.2-1.6).
 */
@DisplayName("MIGRACIÓN 015 (PostgreSQL real): trigger diferido garantiza exactamente un OWNER")
class OwnerDeferredTriggerPostgresIntegrationTest extends PostgresTestcontainerBase {

    @Test
    @DisplayName("1. Demover al único OWNER sin promover reemplazo: el UPDATE no falla, pero el COMMIT sí (deferred)")
    void demoverSinPromoverFallaEnElCommitNoEnElUpdate() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);
            UUID usuarioOwner = crearUsuario(conexion, "owner@test.com");
            UUID organizacion = crearOrganizacion(conexion, usuarioOwner);
            insertarMembresia(conexion, organizacion, usuarioOwner, "OWNER", true);

            try (Statement statement = conexion.createStatement()) {
                // El UPDATE en sí mismo NO debe lanzar: el trigger es DEFERRABLE INITIALLY DEFERRED.
                statement.execute("UPDATE membresias SET rol_gestion = 'ADMIN' WHERE organizacion_id = '"
                        + organizacion + "' AND usuario_id = '" + usuarioOwner + "'");
            }

            assertThatThrownBy(conexion::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("exactamente un OWNER");
        }
    }

    @Test
    @DisplayName("1b. Mover al único OWNER a otra organización (demovido en el destino): "
            + "la organización de ORIGEN queda sin OWNER y el COMMIT debe fallar")
    void moverUnicoOwnerAOtraOrganizacionDejaOrigenSinOwner() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);
            UUID ownerOrigen = crearUsuario(conexion, "owner-origen@test.com");
            UUID ownerDestino = crearUsuario(conexion, "owner-destino@test.com");
            UUID organizacionOrigen = crearOrganizacion(conexion, ownerOrigen);
            UUID organizacionDestino = crearOrganizacion(conexion, ownerDestino);
            insertarMembresia(conexion, organizacionOrigen, ownerOrigen, "OWNER", true);
            insertarMembresia(conexion, organizacionDestino, ownerDestino, "OWNER", true);

            try (Statement statement = conexion.createStatement()) {
                // El destino queda con un solo OWNER (ownerDestino) porque esta fila entra
                // como ADMIN: la revisión del destino NO detecta el problema. Solo revisar
                // también el origen (organizacionOrigen, que se queda con cero OWNER) expone
                // el bug que esta migración debe prevenir.
                statement.execute("UPDATE membresias SET organizacion_id = '" + organizacionDestino
                        + "', rol_gestion = 'ADMIN' WHERE organizacion_id = '" + organizacionOrigen
                        + "' AND usuario_id = '" + ownerOrigen + "'");
            }

            assertThatThrownBy(conexion::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("exactamente un OWNER")
                    .hasMessageContaining(organizacionOrigen.toString());
        }
    }

    @Test
    @DisplayName("2. Demover al OWNER actual Y promover un reemplazo en la MISMA transacción: el COMMIT tiene éxito (swap)")
    void demoverYPromoverEnLaMismaTransaccionTieneExito() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);
            UUID owner = crearUsuario(conexion, "owner2@test.com");
            UUID nuevoOwner = crearUsuario(conexion, "nuevo-owner@test.com");
            UUID organizacion = crearOrganizacion(conexion, owner);
            insertarMembresia(conexion, organizacion, owner, "OWNER", true);
            insertarMembresia(conexion, organizacion, nuevoOwner, "ADMIN", false);

            try (Statement statement = conexion.createStatement()) {
                statement.execute("UPDATE membresias SET rol_gestion = 'ADMIN' WHERE organizacion_id = '"
                        + organizacion + "' AND usuario_id = '" + owner + "'");
                statement.execute("UPDATE membresias SET rol_gestion = 'OWNER' WHERE organizacion_id = '"
                        + organizacion + "' AND usuario_id = '" + nuevoOwner + "'");
            }

            conexion.commit();
            conexion.setAutoCommit(true);

            try (Statement statement = conexion.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT usuario_id FROM membresias WHERE organizacion_id = '" + organizacion
                                 + "' AND rol_gestion = 'OWNER'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat((UUID) resultSet.getObject(1)).isEqualTo(nuevoOwner);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("3a. Actualizar SOLO es_terapeuta en la fila del OWNER: el COMMIT tiene éxito sin disparar ninguna excepción")
    void actualizarSoloEsTerapeutaCommiteaSinError() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);
            UUID owner = crearUsuario(conexion, "owner3@test.com");
            UUID organizacion = crearOrganizacion(conexion, owner);
            insertarMembresia(conexion, organizacion, owner, "OWNER", true);

            try (Statement statement = conexion.createStatement()) {
                statement.execute("UPDATE membresias SET es_terapeuta = FALSE WHERE organizacion_id = '"
                        + organizacion + "' AND usuario_id = '" + owner + "'");
            }
            conexion.commit();
            conexion.setAutoCommit(true);

            try (Statement statement = conexion.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT es_terapeuta FROM membresias WHERE organizacion_id = '" + organizacion
                                 + "' AND usuario_id = '" + owner + "'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBoolean(1)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("3b. El trigger declara UPDATE OF exactamente (rol_gestion, organizacion_id) — es_terapeuta queda fuera a propósito")
    void elTriggerSoloEscuchaRolGestionYOrganizacionId() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");

            Set<String> columnasEscuchadas = new HashSet<>();
            try (Statement statement = conexion.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT a.attname FROM pg_trigger t, pg_attribute a "
                                 + "WHERE t.tgname = 'trg_membresias_un_owner' "
                                 + "AND a.attrelid = t.tgrelid AND a.attnum = ANY(t.tgattr)")) {
                while (resultSet.next()) {
                    columnasEscuchadas.add(resultSet.getString(1));
                }
            }

            assertThat(columnasEscuchadas).containsExactlyInAnyOrder("rol_gestion", "organizacion_id");
        }
    }

    @Test
    @DisplayName("4a. Eliminar una membresía que NO es OWNER: el COMMIT tiene éxito "
            + "(el DELETE no debe fallar por referenciar NEW, que no está asignado)")
    void eliminarMembresiaNoOwnerCommiteaSinError() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);
            UUID owner = crearUsuario(conexion, "owner4a@test.com");
            UUID admin = crearUsuario(conexion, "admin4a@test.com");
            UUID organizacion = crearOrganizacion(conexion, owner);
            insertarMembresia(conexion, organizacion, owner, "OWNER", true);
            insertarMembresia(conexion, organizacion, admin, "ADMIN", false);

            try (Statement statement = conexion.createStatement()) {
                statement.execute("DELETE FROM membresias WHERE organizacion_id = '" + organizacion
                        + "' AND usuario_id = '" + admin + "'");
            }

            conexion.commit();
            conexion.setAutoCommit(true);

            try (Statement statement = conexion.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT COUNT(*) FROM membresias WHERE organizacion_id = '" + organizacion
                                 + "' AND rol_gestion = 'OWNER'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("4b. Eliminar una organización (su membresía OWNER cae por ON DELETE CASCADE): "
            + "el COMMIT tiene éxito en vez de abortar por 'record \"new\" is not assigned yet'")
    void eliminarOrganizacionConCascadaAMembresiaOwnerCommiteaSinError() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, "-- MIGRACIÓN 017");
            conexion.setAutoCommit(false);
            UUID owner = crearUsuario(conexion, "owner4b@test.com");
            UUID organizacion = crearOrganizacion(conexion, owner);
            insertarMembresia(conexion, organizacion, owner, "OWNER", true);

            try (Statement statement = conexion.createStatement()) {
                // ON DELETE CASCADE (fk_membresia_organizacion) borra la membresía OWNER en la misma
                // sentencia; antes del fix, fn_verificar_un_owner() abortaba con "record \"new\" is
                // not assigned yet" al intentar leer NEW.organizacion_id dentro de COALESCE.
                statement.execute("DELETE FROM organizaciones WHERE id = '" + organizacion + "'");
            }

            conexion.commit();
            conexion.setAutoCommit(true);

            try (Statement statement = conexion.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT COUNT(*) FROM organizaciones WHERE id = '" + organizacion + "'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
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

    private UUID crearOrganizacion(Connection conexion, UUID creadoPorId) throws SQLException {
        try (Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO organizaciones (nombre, creado_por_id) VALUES ('Consultorio Test', '"
                             + creadoPorId + "') RETURNING id")) {
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
}
