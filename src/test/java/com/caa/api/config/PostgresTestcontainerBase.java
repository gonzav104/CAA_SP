package com.caa.api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base compartida para los tests de invariantes exclusivos de PostgreSQL de la Fase 1
 * (índice único parcial, trigger de restricción diferido, FKs compuestos, backfill real).
 * <p>
 * H2 no puede ejercitar ninguno de esos comportamientos (sin índices parciales con WHERE, sin
 * triggers de restricción diferidos, sin el mismo enforcement de FK compuestos) — de ahí que
 * estos tests arranquen un contenedor PostgreSQL real en lugar de usar {@code @SpringBootTest}
 * con H2 como el resto de la suite.
 * <p>
 * Deliberadamente NO usa {@code @SpringBootTest}: estos tests ejercitan la base de datos
 * directamente vía JDBC puro, sin necesidad de levantar el contexto de Spring completo
 * (seguridad, Resend, Cloudinary, JWT, etc.), lo que los mantiene rápidos y sin dependencias
 * de configuración ajenas a lo que realmente se está probando.
 * <p>
 * El contenedor es un campo de INSTANCIA (no {@code static}): JUnit 5 + Testcontainers arranca
 * una base de datos nueva y vacía por cada método de test. Es deliberado: la primera parte de
 * {@code init.sql} (tipos/tablas de la inicialización original) NO es idempotente — solo las
 * MIGRACIÓN NNN lo son — así que re-aplicar el archivo completo contra una base ya inicializada
 * (lo que ocurriría con un contenedor compartido {@code static} entre métodos) fallaría con
 * "ya existe". Un contenedor por método evita ese problema sin necesitar lógica de limpieza.
 */
@Testcontainers
public abstract class PostgresTestcontainerBase {

    @Container
    final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    protected Connection abrirConexion() throws SQLException {
        Connection conexion = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        conexion.setAutoCommit(true);
        return conexion;
    }

    /**
     * Ejecuta un script SQL completo (potencialmente con varias sentencias separadas por {@code ;},
     * incluyendo bloques {@code DO $$ ... $$;}) en una sola llamada JDBC. El protocolo simple de
     * PostgreSQL soporta múltiples sentencias por mensaje y respeta el dollar-quoting del lado del
     * servidor, así que esto es seguro incluso para los bloques {@code DO}/función/trigger de las
     * MIGRACIÓN 011-015.
     */
    protected static void ejecutarScript(Connection conexion, String sql) throws SQLException {
        try (Statement statement = conexion.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * Lee {@code init.sql} completo y devuelve el prefijo HASTA (sin incluir) la primera
     * ocurrencia de {@code marcaInicioExclusiva} — p. ej. {@code "-- MIGRACIÓN 013"} para obtener
     * todo el contenido hasta el final de la MIGRACIÓN 012 inclusive. {@code null} devuelve el
     * archivo completo.
     */
    protected static String leerInitSqlHasta(String marcaInicioExclusiva) throws IOException {
        String completo = Files.readString(Path.of("init.sql"), StandardCharsets.UTF_8);
        if (marcaInicioExclusiva == null) {
            return completo;
        }
        int indice = completo.indexOf(marcaInicioExclusiva);
        if (indice < 0) {
            throw new IllegalStateException(
                    "init.sql no contiene el marcador esperado: " + marcaInicioExclusiva);
        }
        return completo.substring(0, indice);
    }

    protected static void aplicarInitSqlHasta(Connection conexion, String marcaInicioExclusiva)
            throws IOException, SQLException {
        ejecutarScript(conexion, leerInitSqlHasta(marcaInicioExclusiva));
    }
}
