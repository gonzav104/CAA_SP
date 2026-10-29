package com.caa.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.caa.api.config.DataSeeder;
import com.caa.api.dtos.AceptarInvitacionDTO;
import com.caa.api.dtos.AceptarInvitacionResponseDTO;
import com.caa.api.dtos.InvitacionOrganizacionRegistroDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.dtos.OrganizacionRegistroDTO;
import com.caa.api.dtos.OrganizacionResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.services.EmailService;
import com.caa.api.services.InvitacionService;
import com.caa.api.services.OrganizacionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regresión: los UPDATE condicionales de {@link InvitacionRepository} (aceptar y revocar) fallaban con
 * HTTP 500 sobre PostgreSQL real porque un literal de enum en un UPDATE HQL se emite como
 * {@code cast(... as estadoinvitacion)}, un tipo que no existe (el enum de la base es
 * {@code estado_invitacion}). H2 crea el tipo a partir del nombre de la clase y por eso lo ocultaba, así
 * que esto se ejercita contra PostgreSQL 15 con el esquema canónico {@code bootstrap.sql}.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never", "spring.jpa.show-sql=false",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000", "cloudinary.cloud-name=test", "cloudinary.api-key=test",
        "cloudinary.api-secret=test", "resend.api-key=test", "app.resend.health-check.enabled=false",
        "app.rate-limit.enabled=false"
})
@DisplayName("Invitaciones sobre PostgreSQL 15 real — aceptar y revocar con UPDATE condicional")
class InvitacionPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("bootstrap.sql")));
        }
    }

    @Autowired InvitacionService invitaciones;
    @Autowired OrganizacionService organizaciones;
    @Autowired InvitacionRepository invitacionRepository;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService email;
    @MockitoBean DataSeeder seeder;

    private Usuario owner;
    private OrganizacionResponseDTO organizacion;

    @BeforeEach
    void setup() {
        jdbc.execute("TRUNCATE usuarios CASCADE");
        owner = usuario("owner@invitaciones.test");
        organizacion = organizaciones.crear(new OrganizacionRegistroDTO("Centro", true), owner.getEmail());
    }

    @Test
    @DisplayName("aceptación válida: crea la membresía propuesta y deja la invitación ACEPTADA")
    void aceptacionValida() {
        Usuario invitado = usuario("invitado@invitaciones.test");
        InvitacionResponseDTO invitacion = invitar(invitado.getEmail(), RolGestion.ADMIN, true);
        String token = ultimoToken();

        AceptarInvitacionResponseDTO resultado = invitaciones.aceptar(new AceptarInvitacionDTO(token), invitado.getEmail());

        assertThat(resultado.organizacionId()).isEqualTo(organizacion.id());
        assertThat(estado(invitacion.id())).isEqualTo("ACEPTADA");
        assertThat(jdbc.queryForObject("SELECT aceptada_por_id FROM invitaciones WHERE id = ?", UUID.class, invitacion.id()))
                .isEqualTo(invitado.getId());
        assertThat(jdbc.queryForObject(
                "SELECT rol_gestion::text || ':' || es_terapeuta FROM membresias WHERE usuario_id = ? AND organizacion_id = ?",
                String.class, invitado.getId(), organizacion.id())).isEqualTo("ADMIN:true");
    }

    @Test
    @DisplayName("invitación ya consumida: un segundo intento recibe el error genérico y no crea nada")
    void invitacionYaConsumida() {
        Usuario invitado = usuario("consumida@invitaciones.test");
        invitar(invitado.getEmail(), RolGestion.MIEMBRO, false);
        String token = ultimoToken();
        invitaciones.aceptar(new AceptarInvitacionDTO(token), invitado.getEmail());

        assertThatThrownBy(() -> invitaciones.aceptar(new AceptarInvitacionDTO(token), invitado.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El enlace no es válido o ha expirado");
        assertThat(cuentaMembresias(invitado)).isEqualTo(1);
    }

    @Test
    @DisplayName("revocación: pasa a REVOCADA, es idempotente y no deshace una aceptada")
    void revocacion() {
        Usuario invitado = usuario("revocada@invitaciones.test");
        InvitacionResponseDTO invitacion = invitar(invitado.getEmail(), RolGestion.MIEMBRO, false);

        invitaciones.revocarOrganizacion(organizacion.id(), invitacion.id(), owner.getEmail());
        assertThat(estado(invitacion.id())).isEqualTo("REVOCADA");
        assertThat(jdbc.queryForObject("SELECT resuelta_en IS NOT NULL FROM invitaciones WHERE id = ?", Boolean.class, invitacion.id())).isTrue();

        invitaciones.revocarOrganizacion(organizacion.id(), invitacion.id(), owner.getEmail());
        assertThat(estado(invitacion.id())).isEqualTo("REVOCADA");

        Usuario otro = usuario("aceptada@invitaciones.test");
        InvitacionResponseDTO aceptada = invitar(otro.getEmail(), RolGestion.MIEMBRO, false);
        invitaciones.aceptar(new AceptarInvitacionDTO(ultimoToken()), otro.getEmail());
        invitaciones.revocarOrganizacion(organizacion.id(), aceptada.id(), owner.getEmail());
        assertThat(estado(aceptada.id())).isEqualTo("ACEPTADA");
    }

    @Test
    @DisplayName("invitación revocada: aceptarla da el error genérico y no crea membresía")
    void invitacionRevocada() {
        Usuario invitado = usuario("revocada2@invitaciones.test");
        InvitacionResponseDTO invitacion = invitar(invitado.getEmail(), RolGestion.MIEMBRO, false);
        String token = ultimoToken();
        invitaciones.revocarOrganizacion(organizacion.id(), invitacion.id(), owner.getEmail());

        assertThatThrownBy(() -> invitaciones.aceptar(new AceptarInvitacionDTO(token), invitado.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(cuentaMembresias(invitado)).isZero();
        assertThat(estado(invitacion.id())).isEqualTo("REVOCADA");
    }

    @Test
    @DisplayName("email distinto o invitación vencida: mismo error genérico y la invitación sigue PENDIENTE")
    void emailDistintoYVencida() {
        Usuario invitado = usuario("destino@invitaciones.test");
        Usuario intruso = usuario("intruso@invitaciones.test");
        InvitacionResponseDTO invitacion = invitar(invitado.getEmail(), RolGestion.MIEMBRO, false);
        String token = ultimoToken();

        assertThatThrownBy(() -> invitaciones.aceptar(new AceptarInvitacionDTO(token), intruso.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(cuentaMembresias(intruso)).isZero();

        jdbc.update("UPDATE invitaciones SET expira_en = now() - interval '1 minute' WHERE id = ?", invitacion.id());
        assertThatThrownBy(() -> invitaciones.aceptar(new AceptarInvitacionDTO(token), invitado.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(estado(invitacion.id())).isEqualTo("PENDIENTE");
        assertThat(cuentaMembresias(invitado)).isZero();
    }

    @Test
    @DisplayName("concurrencia: dos aceptaciones simultáneas, exactamente una gana")
    void dosAceptacionesSimultaneas() throws Exception {
        Usuario a = usuario("hilo-a@invitaciones.test");
        Usuario b = usuario("hilo-b@invitaciones.test");
        InvitacionResponseDTO invitacion = invitar("carrera@invitaciones.test", RolGestion.MIEMBRO, false);

        List<Integer> filas = enParalelo(
                () -> invitacionRepository.aceptarSiVigente(invitacion.id(), a, LocalDateTime.now()),
                () -> invitacionRepository.aceptarSiVigente(invitacion.id(), b, LocalDateTime.now()));

        assertThat(filas).containsExactlyInAnyOrder(0, 1);
        assertThat(estado(invitacion.id())).isEqualTo("ACEPTADA");
    }

    @Test
    @DisplayName("concurrencia: aceptar y revocar a la vez, exactamente uno transiciona y el estado final coincide")
    void aceptarYRevocarSimultaneos() throws Exception {
        Usuario quien = usuario("hilo-c@invitaciones.test");
        InvitacionResponseDTO invitacion = invitar("carrera2@invitaciones.test", RolGestion.MIEMBRO, false);

        List<Integer> filas = enParalelo(
                () -> invitacionRepository.aceptarSiVigente(invitacion.id(), quien, LocalDateTime.now()),
                () -> invitacionRepository.revocarSiPendiente(invitacion.id(), LocalDateTime.now()));

        assertThat(filas).containsExactlyInAnyOrder(0, 1);
        String final_ = estado(invitacion.id());
        assertThat(final_).isIn("ACEPTADA", "REVOCADA");
        // Quien perdió la carrera no deja rastro: solo una aceptada tiene aceptada_por_id.
        assertThat(jdbc.queryForObject("SELECT aceptada_por_id IS NOT NULL FROM invitaciones WHERE id = ?", Boolean.class, invitacion.id()))
                .isEqualTo("ACEPTADA".equals(final_));
    }

    // ───────────────────────── helpers ─────────────────────────

    private Usuario usuario(String correo) {
        return usuarios.save(Usuario.builder().email(correo).passwordHash(passwords.encode("Segura123!")).nombre(correo).build());
    }

    private InvitacionResponseDTO invitar(String correo, RolGestion rol, boolean esTerapeuta) {
        return invitaciones.crearOrganizacion(organizacion.id(),
                new InvitacionOrganizacionRegistroDTO(correo, rol, esTerapeuta), owner.getEmail());
    }

    private String ultimoToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(email, org.mockito.Mockito.atLeastOnce())
                .enviarInvitacionOrganizacion(any(), any(), any(), anyBoolean(), token.capture());
        return token.getValue();
    }

    private String estado(UUID invitacionId) {
        return jdbc.queryForObject("SELECT estado::text FROM invitaciones WHERE id = ?", String.class, invitacionId);
    }

    private int cuentaMembresias(Usuario usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM membresias WHERE usuario_id = ? AND organizacion_id = ?",
                Integer.class, usuario.getId(), organizacion.id());
    }

    @SafeVarargs
    private static List<Integer> enParalelo(java.util.concurrent.Callable<Integer>... tareas) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tareas.length);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new java.util.ArrayList<>();
            for (var tarea : tareas) {
                futuros.add(executor.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<Integer> resultados = new java.util.ArrayList<>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(15, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }
}
