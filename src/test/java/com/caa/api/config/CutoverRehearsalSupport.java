package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.testcontainers.containers.PostgreSQLContainer;

/** Disposable-only helpers. Connections and commands come exclusively from the owned container. */
final class CutoverRehearsalSupport {
    private CutoverRehearsalSupport() {}

    static String migration016() throws Exception {
        String sql = Files.readString(Path.of("init.sql"));
        int start = sql.indexOf("-- MIGRACIÓN 016");
        assertThat(start).as("separate migration 016 block").isGreaterThanOrEqualTo(0);
        int end = sql.indexOf("-- MIGRACIÓN 017", start);
        return sql.substring(start, end < 0 ? sql.length() : end);
    }

    static long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    static Map<String, List<String>> snapshot(Connection connection) throws Exception {
        Map<String, List<String>> snapshot = new TreeMap<>();
        List<String> tables = rows(connection, "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
        for (String table : tables) {
            snapshot.put(table, rows(connection, "SELECT row_to_json(t)::text FROM public.\"" + table
                    + "\" t ORDER BY row_to_json(t)::text"));
        }
        snapshot.put("schema.columns", rows(connection, """
                SELECT table_name || ':' || column_name || ':' || is_nullable || ':' || udt_name
                       || ':' || coalesce(column_default, '')
                FROM information_schema.columns WHERE table_schema='public'
                ORDER BY table_name, ordinal_position
                """));
        snapshot.put("schema.constraints", rows(connection, """
                SELECT conrelid::regclass::text || ':' || conname || ':' || pg_get_constraintdef(oid)
                FROM pg_constraint WHERE connamespace='public'::regnamespace ORDER BY 1
                """));
        snapshot.put("schema.indexes", rows(connection,
                "SELECT indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY indexname"));
        snapshot.put("schema.triggers", rows(connection, """
                SELECT pg_get_triggerdef(oid) FROM pg_trigger
                WHERE NOT tgisinternal AND tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace)
                ORDER BY tgname
                """));
        snapshot.put("schema.functions", rows(connection, """
                SELECT pg_get_functiondef(oid) FROM pg_proc
                WHERE pronamespace='public'::regnamespace AND proname='fn_verificar_un_owner' ORDER BY oid
                """));
        snapshot.put("schema.enums", rows(connection, """
                SELECT t.typname || ':' || e.enumlabel FROM pg_enum e JOIN pg_type t ON t.oid=e.enumtypid
                WHERE t.typnamespace='public'::regnamespace ORDER BY t.typname,e.enumsortorder
                """));
        return snapshot;
    }

    static List<String> rows(Connection connection, String sql) throws Exception {
        List<String> result = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    static void command(PostgreSQLContainer<?> postgres, String... command) throws Exception {
        var result = postgres.execInContainer(command);
        assertThat(result.getExitCode()).as("container command: %s; stderr: %s",
                command[0], result.getStderr()).isZero();
    }

    static void backup(PostgreSQLContainer<?> postgres) throws Exception {
        command(postgres, "pg_dump", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
                "--format=custom", "--file=/tmp/pre016.dump");
    }

    static void restore(PostgreSQLContainer<?> postgres) throws Exception {
        // Only the isolated container database, never a mounted volume or ambient DSN.
        command(postgres, "pg_restore", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
                "--clean", "--if-exists", "--exit-on-error", "--single-transaction", "/tmp/pre016.dump");
    }
}
