package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class GoogleIdentityMigrationPostgresIntegrationTest extends PostgresTestcontainerBase {
    static String migration018() throws Exception {
        String sql = Files.readString(Path.of("init.sql"));
        return sql.substring(sql.indexOf("-- MIGRACIÓN 018"));
    }

    @Test
    void additiveUpgradePreservesAllExistingValuesAndRerunsWithoutChanges() throws Exception {
        try (Connection connection = abrirConexion()) {
            LegacyCleanupPostgresSupport.seedPre017(connection);
            ejecutarScript(connection, LegacyCleanupPostgresSupport.migration017());
            var before = retainedData(connection);
            var beforeColumns = CutoverRehearsalSupport.rows(connection, """
                    SELECT table_name || ':' || column_name || ':' || data_type || ':' || is_nullable
                    FROM information_schema.columns WHERE table_schema='public' ORDER BY 1
                    """);
            ejecutarScript(connection, migration018());
            assertThat(retainedData(connection)).isEqualTo(before);
            assertThat(CutoverRehearsalSupport.rows(connection, """
                    SELECT table_name || ':' || column_name || ':' || data_type || ':' || is_nullable
                    FROM information_schema.columns WHERE table_schema='public'
                    AND column_name NOT LIKE 'google_%' ORDER BY 1
                    """)).isEqualTo(beforeColumns);
            assertThat(CutoverRehearsalSupport.count(connection, """
                    SELECT count(*) FROM usuarios WHERE google_sub IS NOT NULL
                    OR google_recovery_token_hash IS NOT NULL OR google_recovery_expires_at IS NOT NULL
                    OR google_recovery_sub IS NOT NULL OR google_recovery_email IS NOT NULL
                    """)).isZero();
            var after = LegacyCleanupPostgresSupport.fullSnapshot(connection);
            ejecutarScript(connection, migration018());
            assertThat(LegacyCleanupPostgresSupport.fullSnapshot(connection)).isEqualTo(after);
            assertThat(CutoverRehearsalSupport.count(connection,
                    "SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated"))
                    .isZero();
        }
    }

    @Test
    void subjectUniquenessAndCompletePurposeBoundChallengesAreEnforced() throws Exception {
        try (Connection c = abrirConexion()) {
            aplicarInitSqlHasta(c, null);
            ejecutarScript(c, """
                    INSERT INTO usuarios(email,password_hash,nombre) VALUES
                    ('one@example.test','hash','One'), ('two@example.test','hash','Two');
                    UPDATE usuarios SET google_sub='unique-sub' WHERE email='one@example.test';
                    """);
            assertThatThrownBy(() -> ejecutarScript(c,
                    "UPDATE usuarios SET google_sub='unique-sub' WHERE email='two@example.test'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uq_usuarios_google_sub");
            assertThatThrownBy(() -> ejecutarScript(c,
                    "UPDATE usuarios SET google_sub='  ' WHERE email='two@example.test'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_usuarios_google_sub");
            assertThatThrownBy(() -> ejecutarScript(c,
                    "UPDATE usuarios SET google_recovery_token_hash=repeat('a',64) WHERE email='two@example.test'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_usuarios_google_recovery");
            ejecutarScript(c, """
                    UPDATE usuarios SET google_recovery_token_hash=repeat('a',64),
                    google_recovery_expires_at=CURRENT_TIMESTAMP, google_recovery_sub='proposed-sub',
                    google_recovery_email=email WHERE email='two@example.test'
                    """);
        }
    }

    @Test
    void pre017AndPartialOrChangedSchemasFailWithoutMutatingData() throws Exception {
        try (Connection c = abrirConexion()) {
            LegacyCleanupPostgresSupport.seedPre017(c);
            rejectsUnchanged(c, "018: expected post-017");
            ejecutarScript(c, LegacyCleanupPostgresSupport.migration017());
            ejecutarScript(c, "ALTER TABLE usuarios ADD COLUMN google_sub varchar(255)");
            rejectsUnchanged(c, "018: partial");
        }
    }

    @Test
    void changedConstraintOrColumnOnRerunIsRejected() throws Exception {
        try (Connection c = abrirConexion()) {
            aplicarInitSqlHasta(c, null);
            ejecutarScript(c, "ALTER TABLE usuarios ALTER COLUMN google_sub SET DEFAULT 'unsafe'");
            rejectsUnchanged(c, "018: unexpected");
        }
    }

    private void rejectsUnchanged(Connection c, String message) throws Exception {
        var before = LegacyCleanupPostgresSupport.fullSnapshot(c);
        assertThatThrownBy(() -> ejecutarScript(c, migration018())).isInstanceOf(SQLException.class)
                .hasMessageContaining(message);
        ejecutarScript(c, "ROLLBACK");
        assertThat(LegacyCleanupPostgresSupport.fullSnapshot(c)).isEqualTo(before);
    }

    private Map<String, List<String>> retainedData(Connection c) throws Exception {
        Map<String, List<String>> rows = new TreeMap<>();
        for (String table : CutoverRehearsalSupport.rows(c,
                "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY 1")) {
            String projection = table.equals("usuarios")
                    ? "to_jsonb(t) - ARRAY['google_sub','google_recovery_token_hash','google_recovery_expires_at',"
                      + "'google_recovery_sub','google_recovery_email']" : "to_jsonb(t)";
            rows.put(table, CutoverRehearsalSupport.rows(c,
                    "SELECT (" + projection + ")::text FROM public.\"" + table + "\" t ORDER BY 1"));
        }
        return rows;
    }
}
