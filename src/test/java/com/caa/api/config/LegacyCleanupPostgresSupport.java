package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Test-only helpers: all connections belong to a disposable Testcontainer. */
final class LegacyCleanupPostgresSupport {
    private LegacyCleanupPostgresSupport() {}

    static String migration017() throws Exception {
        String sql = Files.readString(Path.of("init.sql"));
        int start = sql.indexOf("-- MIGRACIÓN 017");
        assertThat(start).as("isolated migration 017 exists").isNotNegative();
        return sql.substring(start);
    }

    static void seedPre017(Connection connection) throws Exception {
        PostgresTestcontainerBase.aplicarInitSqlHasta(connection, "-- MIGRACIÓN 016");
        PostgresTestcontainerBase.ejecutarScript(connection,
                Files.readString(Path.of("src/test/resources/cutover-rehearsal/pre-cutover.sql")));
        PostgresTestcontainerBase.ejecutarScript(connection, CutoverRehearsalSupport.migration016());
        PostgresTestcontainerBase.ejecutarScript(connection, """
                INSERT INTO pictogramas_globales(id,etiqueta,imagen_url,arasaac_id)
                VALUES ('30000000-0000-0000-0000-000000000001','Global','https://example.test/global',123);
                INSERT INTO pictogramas_custom(id,paciente_id,etiqueta,imagen_url)
                VALUES ('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001',
                        'Custom','https://example.test/custom');
                INSERT INTO cartillas(id,paciente_id,creador_id,nombre,es_principal)
                VALUES ('40000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
                        '00000000-0000-0000-0000-000000000001','Preserved board',true);
                INSERT INTO categorias(id,cartilla_id,nombre)
                VALUES ('50000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001','Category');
                INSERT INTO items_cartilla(categoria_id,texto_hablado,texto_visible,recurso_global_id)
                VALUES ('50000000-0000-0000-0000-000000000001','Speak','Visible','30000000-0000-0000-0000-000000000001');
                INSERT INTO invitaciones(tipo,paciente_id,email,permiso_propuesto,token_hash,expira_en,invitado_por_id)
                VALUES ('PACIENTE_FAMILIAR','20000000-0000-0000-0000-000000000001','invite@example.test','LECTURA',
                        repeat('a',64),'2030-01-01','00000000-0000-0000-0000-000000000001');
                """);
    }

    static Map<String, List<String>> fullSnapshot(Connection connection) throws Exception {
        var snapshot = CutoverRehearsalSupport.snapshot(connection);
        snapshot.put("schema.columns", CutoverRehearsalSupport.rows(connection, """
                SELECT c.relname || ':' || a.attname || ':' || format_type(a.atttypid,a.atttypmod)
                    || ':' || a.attnotnull || ':' || a.attidentity::text || ':' || a.attgenerated::text
                    || ':' || coalesce(pg_get_expr(d.adbin,d.adrelid), '')
                FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid
                LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                WHERE c.relnamespace='public'::regnamespace AND c.relkind IN ('r','p')
                  AND a.attnum>0 AND NOT a.attisdropped ORDER BY c.relname,a.attname
                """));
        snapshot.put("schema.views", CutoverRehearsalSupport.rows(connection,
                "SELECT schemaname || '.' || viewname || ':' || definition FROM pg_views "
                        + "WHERE schemaname='public' ORDER BY 1"));
        snapshot.put("schema.functions", CutoverRehearsalSupport.rows(connection,
                "SELECT pg_get_functiondef(oid) FROM pg_proc "
                        + "WHERE pronamespace='public'::regnamespace AND prokind IN ('f','p') ORDER BY proname,oid"));
        return snapshot;
    }

    static Map<String, List<String>> retainedData(Connection connection) throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        for (String table : CutoverRehearsalSupport.rows(connection,
                "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")) {
            String projection = switch (table) {
                case "usuarios" -> "to_jsonb(t) - 'rol'";
                case "pacientes" -> "to_jsonb(t) - 'terapeuta_id'";
                default -> "to_jsonb(t)";
            };
            result.put(table, CutoverRehearsalSupport.rows(connection,
                    "SELECT (" + projection + ")::text FROM public.\"" + table + "\" t ORDER BY 1"));
        }
        return result;
    }
}
