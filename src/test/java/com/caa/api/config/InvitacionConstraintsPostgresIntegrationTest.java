package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThatCode;
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
 * Cierra el hallazgo informativo señalado en la revisión final de la Fase 1: el CHECK
 * {@code chk_invitacion_contexto} y los índices únicos parciales de {@code invitaciones}
 * (MIGRACIÓN 014, init.sql) no tenían cobertura automatizada contra PostgreSQL real.
 * <p>
 * H2 no soporta índices únicos parciales con {@code WHERE} (los omite silenciosamente), así que
 * este es el único test automatizado que prueba, contra la base de datos misma, que: (a) el
 * CHECK rechaza cualquier combinación que mezcle o deje incompletos los campos propuestos de
 * ORGANIZACION/PACIENTE_FAMILIAR; (b) los índices únicos parciales rechazan una segunda
 * invitación PENDIENTE para el mismo (organización, email) o (paciente, email), pero permiten una
 * nueva una vez que la anterior ya no está PENDIENTE. No es RED/GREEN de TDD: verifica DDL ya
 * escrita (MIGRACIÓN 014, Fase 1).
 */
@DisplayName("MIGRACIÓN 014 (PostgreSQL real): chk_invitacion_contexto + índices únicos parciales de invitaciones")
class InvitacionConstraintsPostgresIntegrationTest extends PostgresTestcontainerBase {

    @Test
    @DisplayName("1. Invitación ORGANIZACION válida (rolGestion ADMIN/MIEMBRO + esTerapeuta no nulo, sin paciente/permiso) → aceptada")
    void organizacionValida_esAceptada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner1@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);

            assertThatCode(() -> insertarInvitacionOrganizacion(conexion, org, "nuevo1@test.com",
                    "MIEMBRO", "TRUE", owner, "PENDIENTE"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("2. rolGestionPropuesto=OWNER en una invitación ORGANIZACION → rechazada por el CHECK")
    void organizacionConRolOwner_esRechazada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner2@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);

            assertThatThrownBy(() -> insertarInvitacionOrganizacion(conexion, org, "nuevo2@test.com",
                    "OWNER", "TRUE", owner, "PENDIENTE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_invitacion_contexto");
        }
    }

    @Test
    @DisplayName("3. esTerapeutaPropuesto NULL en una invitación ORGANIZACION → rechazada por el CHECK")
    void organizacionSinEsTerapeutaPropuesto_esRechazada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner3@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);

            assertThatThrownBy(() -> insertarInvitacionOrganizacion(conexion, org, "nuevo3@test.com",
                    "MIEMBRO", "NULL", owner, "PENDIENTE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_invitacion_contexto");
        }
    }

    @Test
    @DisplayName("4. Invitación ORGANIZACION con paciente_id también seteado (contexto mezclado) → rechazada por el CHECK")
    void organizacionConPacienteIdMezclado_esRechazada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner4@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);
            UUID paciente = crearPaciente(conexion, owner, org);

            String sql = "INSERT INTO invitaciones (tipo, organizacion_id, paciente_id, email, "
                    + "rol_gestion_propuesto, es_terapeuta_propuesto, token_hash, estado, expira_en, invitado_por_id) "
                    + "VALUES ('ORGANIZACION', '" + org + "', '" + paciente + "', 'mezclado@test.com', "
                    + "'MIEMBRO', TRUE, '" + hashUnico() + "', 'PENDIENTE', NOW() + INTERVAL '7 days', '" + owner + "')";

            assertThatThrownBy(() -> ejecutarScript(conexion, sql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_invitacion_contexto");
        }
    }

    @Test
    @DisplayName("5. Invitación PACIENTE_FAMILIAR válida (permiso propuesto, sin rolGestion/esTerapeuta) → aceptada")
    void familiarValida_esAceptada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner5@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);
            UUID paciente = crearPaciente(conexion, owner, org);

            assertThatCode(() -> insertarInvitacionFamiliar(conexion, paciente, "familiar5@test.com",
                    "LECTURA", owner, "PENDIENTE"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("6. Invitación PACIENTE_FAMILIAR con rolGestionPropuesto también seteado (contexto mezclado) → rechazada")
    void familiarConRolGestionMezclado_esRechazada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner6@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);
            UUID paciente = crearPaciente(conexion, owner, org);

            String sql = "INSERT INTO invitaciones (tipo, paciente_id, email, "
                    + "permiso_propuesto, rol_gestion_propuesto, token_hash, estado, expira_en, invitado_por_id) "
                    + "VALUES ('PACIENTE_FAMILIAR', '" + paciente + "', 'mezclado6@test.com', "
                    + "'LECTURA', 'MIEMBRO', '" + hashUnico() + "', 'PENDIENTE', NOW() + INTERVAL '7 days', '" + owner + "')";

            assertThatThrownBy(() -> ejecutarScript(conexion, sql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_invitacion_contexto");
        }
    }

    @Test
    @DisplayName("7. Segunda invitación ORGANIZACION PENDIENTE para el mismo (organización, email) → rechazada por el índice único parcial")
    void segundaInvitacionOrganizacionPendiente_mismoOrgEmail_esRechazada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner7@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);

            insertarInvitacionOrganizacion(conexion, org, "repetido7@test.com", "MIEMBRO", "TRUE", owner, "PENDIENTE");

            assertThatThrownBy(() -> insertarInvitacionOrganizacion(
                    conexion, org, "repetido7@test.com", "ADMIN", "FALSE", owner, "PENDIENTE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_invitaciones_pendiente_org");
        }
    }

    @Test
    @DisplayName("8. Segunda invitación PACIENTE_FAMILIAR PENDIENTE para el mismo (paciente, email) → rechazada por el índice único parcial")
    void segundaInvitacionFamiliarPendiente_mismoPacienteEmail_esRechazada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner8@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);
            UUID paciente = crearPaciente(conexion, owner, org);

            insertarInvitacionFamiliar(conexion, paciente, "repetido8@test.com", "LECTURA", owner, "PENDIENTE");

            assertThatThrownBy(() -> insertarInvitacionFamiliar(
                    conexion, paciente, "repetido8@test.com", "EDICION_LIMITADA", owner, "PENDIENTE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_invitaciones_pendiente_paciente");
        }
    }

    @Test
    @DisplayName("9. Una nueva invitación PENDIENTE para el mismo (organización, email) SÍ se acepta una vez resuelta la anterior")
    void nuevaInvitacionOrganizacionPendiente_trasResolverLaAnterior_esAceptada() throws SQLException, IOException {
        try (Connection conexion = abrirConexion()) {
            aplicarInitSqlHasta(conexion, null);
            conexion.setAutoCommit(false);

            UUID owner = crearUsuario(conexion, "owner9@test.com");
            UUID org = crearOrganizacionConOwner(conexion, owner);

            insertarInvitacionOrganizacion(conexion, org, "resuelto9@test.com", "MIEMBRO", "TRUE", owner, "REVOCADA");

            // El índice único parcial solo aplica WHERE estado = 'PENDIENTE': la anterior ya está
            // REVOCADA, así que una nueva fila PENDIENTE para el mismo (organización, email) no choca.
            assertThatCode(() -> insertarInvitacionOrganizacion(
                    conexion, org, "resuelto9@test.com", "ADMIN", "FALSE", owner, "PENDIENTE"))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

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

    private void insertarInvitacionOrganizacion(Connection conexion, UUID organizacionId, String email,
                                                 String rolGestionPropuesto, String esTerapeutaPropuesto,
                                                 UUID invitadoPorId, String estado) throws SQLException {
        String sql = "INSERT INTO invitaciones (tipo, organizacion_id, email, rol_gestion_propuesto, "
                + "es_terapeuta_propuesto, token_hash, estado, expira_en, invitado_por_id) VALUES ("
                + "'ORGANIZACION', '" + organizacionId + "', '" + email + "', "
                + ("NULL".equals(rolGestionPropuesto) ? "NULL" : "'" + rolGestionPropuesto + "'") + ", "
                + ("NULL".equals(esTerapeutaPropuesto) ? "NULL" : esTerapeutaPropuesto) + ", "
                + "'" + hashUnico() + "', '" + estado + "', NOW() + INTERVAL '7 days', '" + invitadoPorId + "')";
        ejecutarScript(conexion, sql);
    }

    private void insertarInvitacionFamiliar(Connection conexion, UUID pacienteId, String email,
                                             String permisoPropuesto, UUID invitadoPorId, String estado)
            throws SQLException {
        String sql = "INSERT INTO invitaciones (tipo, paciente_id, email, permiso_propuesto, "
                + "token_hash, estado, expira_en, invitado_por_id) VALUES ("
                + "'PACIENTE_FAMILIAR', '" + pacienteId + "', '" + email + "', '" + permisoPropuesto + "', "
                + "'" + hashUnico() + "', '" + estado + "', NOW() + INTERVAL '7 days', '" + invitadoPorId + "')";
        ejecutarScript(conexion, sql);
    }

    private String hashUnico() {
        return "hash-" + UUID.randomUUID();
    }
}
