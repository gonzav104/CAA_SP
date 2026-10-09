package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.caa.api.dtos.TransferenciaPropiedadDTO;
import com.caa.api.services.OrganizacionService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de concurrencia real contra PostgreSQL (design §3.3 punto 3, design-part2 "Testing
 * Strategy": "concurrent transfers (2 threads + latch, no @Transactional on test) end with
 * exactly 1 OWNER") para {@code OrganizacionServiceImpl.transferirPropiedad}: dos transferencias
 * de propiedad concurrentes sobre la MISMA organización, cada una hacia un destino distinto.
 * <p>
 * A diferencia del resto de {@code *PostgresIntegrationTest} (que ejercitan SQL crudo contra un
 * contenedor efímero por método, sin contexto de Spring), este test necesita el bean real
 * {@code OrganizacionServiceImpl} para probar el bloqueo
 * {@code OrganizacionRepository.findConBloqueoById} (@Lock(PESSIMISTIC_WRITE)) de Spring Data JPA
 * tal cual lo ejecuta la aplicación — H2 no ofrece la misma semántica de bloqueo de fila que
 * PostgreSQL real bajo contención genuina. Por eso este único archivo arranca un contexto Spring
 * completo sobre un contenedor PostgreSQL (vía {@code @DynamicPropertySource}), en vez de extender
 * {@code PostgresTestcontainerBase}.
 */
@Testcontainers
@SpringBootTest
@DisplayName("Transferencia de propiedad — concurrencia real contra PostgreSQL")
class TransferenciaPropiedadConcurrenciaPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void propiedadesDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==");
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("cloudinary.cloud-name", () -> "test-cloud");
        registry.add("cloudinary.api-key", () -> "test-api-key");
        registry.add("cloudinary.api-secret", () -> "test-api-secret");
        registry.add("resend.api-key", () -> "test-resend-api-key");
    }

    /** Aplica el {@code init.sql} completo (esquema real, incl. MIGRACIÓN 011-015a) una sola vez. */
    @BeforeAll
    static void aplicarEsquema() throws Exception {
        String sql = Files.readString(Path.of("init.sql"), StandardCharsets.UTF_8);
        try (Connection conexion = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = conexion.createStatement()) {
            statement.execute(sql);
        }
    }

    @Autowired
    private OrganizacionService organizacionService;

    @Test
    @DisplayName("Dos transferencias concurrentes (ownerActual→Y, ownerActual→Z): exactamente una "
            + "tiene éxito, la otra recibe un rechazo claro, y queda exactamente un OWNER")
    void dosTransferenciasConcurrentes_exactamenteUnaExitosaYUnSoloOwner() throws Exception {
        UUID organizacionId;
        UUID ownerId;
        UUID destinoYId;
        UUID destinoZId;
        String emailOwner = "owner-concurrencia@test.com";

        // Una sola transacción para todo el seed: el trigger diferido trg_organizaciones_un_owner
        // (MIGRACIÓN 015) revisa al COMMIT que la organización tenga su OWNER — con autoCommit=true
        // el INSERT de organizaciones comitearía antes de insertar la membresía y fallaría.
        try (Connection conexion = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            conexion.setAutoCommit(false);
            ownerId = crearUsuario(conexion, emailOwner);
            destinoYId = crearUsuario(conexion, "destino-y@test.com");
            destinoZId = crearUsuario(conexion, "destino-z@test.com");
            organizacionId = crearOrganizacion(conexion, ownerId);
            insertarMembresia(conexion, organizacionId, ownerId, "OWNER", true);
            insertarMembresia(conexion, organizacionId, destinoYId, "ADMIN", false);
            insertarMembresia(conexion, organizacionId, destinoZId, "ADMIN", false);
            conexion.commit();
        }

        CountDownLatch salida = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Exception> transferirHaciaY = () -> {
                salida.countDown();
                salida.await();
                try {
                    organizacionService.transferirPropiedad(
                            organizacionId, new TransferenciaPropiedadDTO(destinoYId), emailOwner);
                    return null;
                } catch (Exception ex) {
                    return ex;
                }
            };
            Callable<Exception> transferirHaciaZ = () -> {
                salida.countDown();
                salida.await();
                try {
                    organizacionService.transferirPropiedad(
                            organizacionId, new TransferenciaPropiedadDTO(destinoZId), emailOwner);
                    return null;
                } catch (Exception ex) {
                    return ex;
                }
            };

            Future<Exception> futuroY = executor.submit(transferirHaciaY);
            Future<Exception> futuroZ = executor.submit(transferirHaciaZ);

            Exception excepcionY = futuroY.get(30, TimeUnit.SECONDS);
            Exception excepcionZ = futuroZ.get(30, TimeUnit.SECONDS);
            // Arrays.asList (no List.of): uno de los dos resultados es null (la transferencia
            // ganadora) y List.of no admite elementos null.
            List<Exception> resultados = Arrays.asList(excepcionY, excepcionZ);

            assertThat(resultados).filteredOn(e -> e == null).hasSize(1);
            assertThat(resultados).filteredOn(e -> e != null).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        try (Connection conexion = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = conexion.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM membresias WHERE organizacion_id = '" + organizacionId
                             + "' AND rol_gestion = 'OWNER'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
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
                     "INSERT INTO organizaciones (nombre, creado_por_id) VALUES ('Consultorio Concurrencia', '"
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
