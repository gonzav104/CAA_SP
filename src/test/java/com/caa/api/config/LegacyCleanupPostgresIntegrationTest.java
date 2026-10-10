package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class LegacyCleanupPostgresIntegrationTest extends PostgresTestcontainerBase {
    @Test
    void cleanupPreservesEveryRetainedValueAndVerifiedRerunIsNoOp() throws Exception {
        try (Connection connection = abrirConexion()) {
            LegacyCleanupPostgresSupport.seedPre017(connection);
            var before = LegacyCleanupPostgresSupport.retainedData(connection);
            ejecutarScript(connection, LegacyCleanupPostgresSupport.migration017());
            assertThat(LegacyCleanupPostgresSupport.retainedData(connection)).isEqualTo(before);
            assertThat(CutoverRehearsalSupport.count(connection, """
                    SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
                    AND ((table_name='usuarios' AND column_name='rol')
                      OR (table_name='pacientes' AND column_name='terapeuta_id'))
                    """)).isZero();
            assertThat(CutoverRehearsalSupport.count(connection, """
                    SELECT count(*) FROM pg_constraint WHERE conname='fk_cartilla_creador'
                    AND conrelid='public.cartillas'::regclass
                    """)).isEqualTo(1);
            assertThat(CutoverRehearsalSupport.count(connection,
                    "SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated"))
                    .isZero();
            var after = LegacyCleanupPostgresSupport.fullSnapshot(connection);
            ejecutarScript(connection, LegacyCleanupPostgresSupport.migration017());
            assertThat(LegacyCleanupPostgresSupport.fullSnapshot(connection)).isEqualTo(after);
        }
    }

    @Test
    void rejectsPre016WithoutChangingAnything() throws Exception {
        try (Connection connection = abrirConexion()) {
            aplicarInitSqlHasta(connection, "-- MIGRACIÓN 016");
            rejectsUnchanged(connection, "017: post-cutover");
        }
    }

    @Test
    void rejectsPartialCleanupWithoutChangingAnything() throws Exception {
        try (Connection connection = abrirConexion()) {
            LegacyCleanupPostgresSupport.seedPre017(connection);
            ejecutarScript(connection, "ALTER TABLE usuarios DROP COLUMN rol");
            rejectsUnchanged(connection, "017: partial");
        }
    }

    @Test
    void rejectsUnknownLocalIndex() throws Exception {
        rejectsDependency("CREATE INDEX unexpected_role_idx ON usuarios(rol)", "017: unexpected");
    }

    @Test
    void rejectsUnknownLocalCheck() throws Exception {
        rejectsDependency("ALTER TABLE pacientes ADD CONSTRAINT unexpected_check CHECK (terapeuta_id IS NOT NULL)",
                "017: unexpected");
    }

    @Test
    void rejectsChangedKnownIndexDefinition() throws Exception {
        rejectsDependency("DROP INDEX idx_pacientes_terapeuta; CREATE INDEX idx_pacientes_terapeuta "
                + "ON pacientes(terapeuta_id) WHERE terapeuta_id IS NOT NULL", "017: legacy index");
    }

    @Test
    void rejectsFunctionConsumerWithoutChangingAnything() throws Exception {
        rejectsDependency("CREATE FUNCTION legacy_consumer() RETURNS bigint LANGUAGE sql AS "
                + "'SELECT count(terapeuta_id) FROM public.pacientes'", "017: function");
    }

    @Test
    void viewDependencyOnSecondDropRollsBackFirstDropAndAllData() throws Exception {
        rejectsDependency("CREATE VIEW legacy_consumer AS SELECT terapeuta_id FROM pacientes", "depend");
    }

    private void rejectsDependency(String sql, String expectedMessage) throws Exception {
        try (Connection connection = abrirConexion()) {
            LegacyCleanupPostgresSupport.seedPre017(connection);
            ejecutarScript(connection, sql);
            rejectsUnchanged(connection, expectedMessage);
        }
    }

    private void rejectsUnchanged(Connection connection, String expectedMessage) throws Exception {
        var before = LegacyCleanupPostgresSupport.fullSnapshot(connection);
        String migration = LegacyCleanupPostgresSupport.migration017();
        assertThatThrownBy(() -> ejecutarScript(connection, migration)).isInstanceOf(SQLException.class)
                .hasMessageContaining(expectedMessage);
        assertThatThrownBy(() -> ejecutarScript(connection, "SELECT 1")).isInstanceOf(SQLException.class)
                .hasMessageContaining("transaction is aborted");
        ejecutarScript(connection, "ROLLBACK");
        assertThat(LegacyCleanupPostgresSupport.fullSnapshot(connection)).isEqualTo(before);
    }
}
