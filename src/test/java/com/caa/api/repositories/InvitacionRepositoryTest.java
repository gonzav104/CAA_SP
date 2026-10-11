package com.caa.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.Invitacion;
import com.caa.api.models.Organizacion;
import com.caa.api.models.TipoInvitacion;
import com.caa.api.models.Usuario;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/**
 * Cubre el finding R3-001: {@link InvitacionRepository#aceptarSiVigente} es el único mecanismo
 * documentado que previene un accept doble por carrera (UPDATE condicional PENDIENTE -&gt;
 * ACEPTADA, 0 o 1 filas afectadas). Ningún test previo lo ejercitaba.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caainvitacionrepo;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("InvitacionRepository.aceptarSiVigente — UPDATE condicional de un solo uso (H2)")
class InvitacionRepositoryTest {

    @Autowired private InvitacionRepository invitacionRepository;
    @Autowired private OrganizacionRepository organizacionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("PENDIENTE y vigente: devuelve 1 y transiciona a ACEPTADA")
    void pendienteYVigenteSeAcepta() {
        Usuario quienAcepta = crearUsuario("acepta@invitacion.test");
        Invitacion invitacion = crearInvitacion(LocalDateTime.now().plusDays(1));

        int filas = invitacionRepository.aceptarSiVigente(invitacion.getId(), quienAcepta, LocalDateTime.now());

        assertThat(filas).isEqualTo(1);
        Invitacion recargada = invitacionRepository.findById(invitacion.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoInvitacion.ACEPTADA);
        assertThat(recargada.getAceptadaPor().getId()).isEqualTo(quienAcepta.getId());
    }

    @Test
    @DisplayName("Ya aceptada: el segundo intento devuelve 0 y no cambia el estado")
    void yaAceptadaDevuelveCero() {
        Usuario primero = crearUsuario("primero@invitacion.test");
        Usuario segundo = crearUsuario("segundo@invitacion.test");
        Invitacion invitacion = crearInvitacion(LocalDateTime.now().plusDays(1));

        int primerIntento = invitacionRepository.aceptarSiVigente(invitacion.getId(), primero, LocalDateTime.now());
        int segundoIntento = invitacionRepository.aceptarSiVigente(invitacion.getId(), segundo, LocalDateTime.now());

        assertThat(primerIntento).isEqualTo(1);
        assertThat(segundoIntento).isZero();
        Invitacion recargada = invitacionRepository.findById(invitacion.getId()).orElseThrow();
        assertThat(recargada.getAceptadaPor().getId()).isEqualTo(primero.getId());
    }

    @Test
    @DisplayName("Expirada: devuelve 0 y el estado permanece PENDIENTE")
    void expiradaDevuelveCero() {
        Usuario quienAcepta = crearUsuario("expirada@invitacion.test");
        Invitacion invitacion = crearInvitacion(LocalDateTime.now().minusMinutes(1));

        int filas = invitacionRepository.aceptarSiVigente(invitacion.getId(), quienAcepta, LocalDateTime.now());

        assertThat(filas).isZero();
        Invitacion recargada = invitacionRepository.findById(invitacion.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoInvitacion.PENDIENTE);
    }

    @Test
    @DisplayName("Doble llamada concurrente: exactamente un hilo acepta, el otro recibe 0")
    void dobleLlamadaConcurrenteSoloUnaGana() throws InterruptedException {
        Usuario hiloA = crearUsuario("hiloa@invitacion.test");
        Usuario hiloB = crearUsuario("hilob@invitacion.test");
        Invitacion invitacion = crearInvitacion(LocalDateTime.now().plusDays(1));

        // Dos hilos reales (no una simulación secuencial) llaman al método transaccional del
        // repositorio al mismo tiempo, coordinados por un CountDownLatch para maximizar la
        // superposición. El UPDATE condicional en sí es atómico a nivel de fila en cualquier
        // base relacional (H2 incluido): el segundo UPDATE que llega se serializa detrás del
        // primero y, al re-evaluar el WHERE tras verlo ya en ACEPTADA, afecta 0 filas.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Future<Integer> resultadoA = executor.submit(() -> {
                salida.await();
                return invitacionRepository.aceptarSiVigente(invitacion.getId(), hiloA, LocalDateTime.now());
            });
            Future<Integer> resultadoB = executor.submit(() -> {
                salida.await();
                return invitacionRepository.aceptarSiVigente(invitacion.getId(), hiloB, LocalDateTime.now());
            });
            salida.countDown();

            int filasA = esperar(resultadoA);
            int filasB = esperar(resultadoB);

            assertThat(List.of(filasA, filasB)).containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Invitacion recargada = invitacionRepository.findById(invitacion.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoInvitacion.ACEPTADA);
    }

    private int esperar(Future<Integer> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Usuario crearUsuario(String email) {
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre(email)

                .build());
    }

    private Invitacion crearInvitacion(LocalDateTime expiraEn) {
        Usuario invitadoPor = crearUsuario("invita-" + UUID.randomUUID() + "@invitacion.test");
        Organizacion organizacion = organizacionRepository.save(Organizacion.builder()
                .nombre("Org de prueba")
                .creadoPor(invitadoPor)
                .build());
        return invitacionRepository.save(Invitacion.builder()
                .tipo(TipoInvitacion.ORGANIZACION)
                .organizacion(organizacion)
                .email("invitado-" + UUID.randomUUID() + "@invitacion.test")
                .tokenHash("hash-" + UUID.randomUUID())
                .estado(EstadoInvitacion.PENDIENTE)
                .expiraEn(expiraEn)
                .invitadoPor(invitadoPor)
                .build());
    }
}
