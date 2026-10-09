package com.caa.api.config;

import static com.caa.api.config.CutoverRehearsalSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caa.api.CaaSpApplication;
import com.caa.api.dtos.OrganizacionRegistroDTO;
import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.services.OrganizacionService;
import com.caa.api.services.PacienteService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

class CutoverRehearsalPostgresIntegrationTest extends PostgresTestcontainerBase {
    @Test
    void migration016IsAvailableAsSeparateAtomicBlock() throws Exception {
        assertThat(migration016()).contains("BEGIN;", "COMMIT;",
                "ALTER COLUMN organizacion_id SET NOT NULL",
                "ALTER COLUMN terapeuta_id DROP NOT NULL", "ALTER COLUMN rol DROP NOT NULL");
    }

    @Test
    void disposableCutoverRollbackAndReapplication() throws Exception {
        try (Connection connection = abrirConexion()) {
            assertThat(count(connection, "SELECT current_setting('server_version_num')::int / 10000")).isEqualTo(15);
            aplicarInitSqlHasta(connection, "-- MIGRACIÓN 016");
            ejecutarScript(connection, Files.readString(Path.of("src/test/resources/cutover-rehearsal/pre-cutover.sql")));
            Map<String, List<String>> before = snapshot(connection);
            assertThat(count(connection, "SELECT count(*) FROM pacientes WHERE organizacion_id IS NULL")).isEqualTo(1);
            assertNullability(connection, "YES", "NO", "YES");
            assertOwners(connection);
            backup(POSTGRES);
            System.out.println("REHEARSAL pre: 4 patients, 1 late legacy without organization, OWNER invariant valid; snapshot saved");

            apply016(connection, "first application");
            verifyPostconditions(connection, before);
            Map<String, List<String>> after = snapshot(connection);
            apply016(connection, "idempotent repeat");
            assertThat(snapshot(connection)).isEqualTo(after);
            System.out.println("REHEARSAL post: constraints/data valid; migration-only repeat unchanged");
            backendSmoke();
            System.out.println("REHEARSAL backend: real Spring context and organization/patient service writes succeeded");

            restore(POSTGRES);
            assertThat(snapshot(connection)).as("complete pre016 public schema and data restored").isEqualTo(before);
            assertNullability(connection, "YES", "NO", "YES");
            System.out.println("REHEARSAL rollback: pg_dump/pg_restore restored exact schema and rows including late legacy NULL organization");
            apply016(connection, "reapplication after restore");
            verifyPostconditions(connection, before);
            // Generated organization UUIDs/timestamps can differ after restoration; compare semantics above,
            // and prove exact stable state on another selected-block replay.
            Map<String, List<String>> reapplied = snapshot(connection);
            apply016(connection, "repeat after restored reapplication");
            assertThat(snapshot(connection)).isEqualTo(reapplied);
            System.out.println("REHEARSAL reapplication: same semantic outcomes; selected-block repeat unchanged");
        }
    }

    private void apply016(Connection connection, String phase) throws Exception {
        try {
            ejecutarScript(connection, migration016());
        } catch (SQLException failure) {
            throw new AssertionError("STOP migration 016 phase=" + phase + " SQLSTATE="
                    + failure.getSQLState() + ": " + failure.getMessage(), failure);
        }
    }

    private void assertNullability(Connection connection, String organization, String therapist, String role) throws Exception {
        assertThat(rows(connection, """
                SELECT is_nullable FROM information_schema.columns WHERE table_schema='public'
                AND ((table_name='pacientes' AND column_name IN ('organizacion_id','terapeuta_id'))
                OR (table_name='usuarios' AND column_name='rol')) ORDER BY table_name,column_name
                """)).containsExactly(organization, therapist, role);
    }

    private void assertOwners(Connection connection) throws Exception {
        assertThat(count(connection, """
                SELECT count(*) FROM organizaciones o WHERE
                (SELECT count(*) FROM membresias m WHERE m.organizacion_id=o.id AND m.rol_gestion='OWNER') <> 1
                """)).isZero();
    }

    private void verifyPostconditions(Connection connection, Map<String, List<String>> before) throws Exception {
        assertNullability(connection, "NO", "YES", "YES");
        assertOwners(connection);
        assertThat(count(connection, "SELECT count(*) FROM pacientes WHERE organizacion_id IS NULL")).isZero();
        assertThat(count(connection, "SELECT count(*) FROM pacientes")).isEqualTo(4);
        assertThat(count(connection, "SELECT count(*) FROM pacientes_terapeutas")).isEqualTo(2);
        assertThat(count(connection, """
                SELECT count(*) FROM pacientes p
                JOIN pacientes_terapeutas pt ON pt.paciente_id=p.id AND pt.organizacion_id=p.organizacion_id
                JOIN membresias m ON m.organizacion_id=pt.organizacion_id AND m.usuario_id=pt.usuario_id
                WHERE p.id='20000000-0000-0000-0000-000000000001'
                  AND pt.usuario_id=p.terapeuta_id AND m.rol_gestion='OWNER' AND m.es_terapeuta
                """)).isEqualTo(1);
        assertThat(count(connection, """
                SELECT count(*) FROM pacientes WHERE id IN
                ('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003',
                 '20000000-0000-0000-0000-000000000004')
                AND organizacion_id='10000000-0000-0000-0000-000000000002'
                """)).isEqualTo(3);
        assertThat(count(connection, """
                SELECT count(*) FROM pacientes_terapeutas WHERE paciente_id IN
                ('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000004')
                """)).isZero();
        assertThat(count(connection, """
                SELECT count(*) FROM membresias WHERE usuario_id='00000000-0000-0000-0000-000000000004'
                """)).isZero();
        var current = snapshot(connection);
        for (String table : before.keySet()) {
            if (!table.startsWith("schema.") && !List.of("pacientes", "organizaciones", "membresias", "pacientes_terapeutas").contains(table)) {
                assertThat(current.get(table)).as("unchanged rows: %s", table).isEqualTo(before.get(table));
            }
        }
        // Expected rejection, NOT a migration failure: the new constraint must reject NULL org.
        assertThatThrownBy(() -> ejecutarScript(connection,
                "UPDATE pacientes SET organizacion_id=NULL WHERE id='20000000-0000-0000-0000-000000000001'"))
                .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("23502"));
        // Prove nullable legacy fields through actual writes, then rollback only these probes.
        connection.setAutoCommit(false);
        try {
            ejecutarScript(connection, "UPDATE pacientes SET terapeuta_id=NULL; UPDATE usuarios SET rol=NULL;");
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private void backendSmoke() {
        // Command-line properties override ambient application defaults. Never use a fallback DSN.
        try (var context = new SpringApplicationBuilder(CaaSpApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.hibernate.ddl-auto=none", "--spring.sql.init.mode=never",
                "--jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
                "--jwt.expiration-ms=3600000", "--cloudinary.cloud-name=test", "--cloudinary.api-key=test",
                "--cloudinary.api-secret=test", "--resend.api-key=test", "--app.resend.health-check.enabled=false")) {
            var organizations = context.getBean(OrganizacionService.class);
            var patients = context.getBean(PacienteService.class);
            var organization = organizations.crear(new OrganizacionRegistroDTO("Post cutover", false), "manager@rehearsal.test");
            assertThat(patients.registrarPaciente(organization.id(),
                    new PacienteRegistroDTO("Smoke", "Patient", LocalDate.of(2015, 1, 1)), "manager@rehearsal.test")).isNotNull();
        }
    }
}
