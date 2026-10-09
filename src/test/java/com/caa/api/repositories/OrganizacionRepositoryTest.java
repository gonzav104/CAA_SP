package com.caa.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.caa.api.models.Organizacion;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cubre el finding R3-002: {@link OrganizacionRepository#findConBloqueoById} (PESSIMISTIC_WRITE)
 * es documentado como el primer bloqueo que toda operación que muta membresías debe adquirir.
 * Ningún test previo verificaba que el bloqueo realmente se adquiere ni que bloquea una
 * transacción concurrente.
 * <p>
 * {@code connection-init-sql} sube el LOCK_TIMEOUT de H2 (default 1s) a 10s en cada conexión del
 * pool, para que el margen entre "todavía bloqueado" y "ya liberado" sea cómodo y no dependa de
 * temporización fina — sin esto, un H2 lento en CI podría disparar una excepción de timeout de
 * lock en vez de demostrar el bloqueo real.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caaorganizacionrepo;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.connection-init-sql=SET LOCK_TIMEOUT 10000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("OrganizacionRepository.findConBloqueoById — PESSIMISTIC_WRITE (H2)")
class OrganizacionRepositoryTest {

    @Autowired private OrganizacionRepository organizacionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Devuelve la Organizacion correcta (no solo adquiere el lock)")
    void devuelveLaOrganizacionCorrecta() {
        Organizacion organizacion = crearOrganizacion();
        crearOrganizacion(); // una segunda fila para descartar que devuelva "cualquiera"

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Organizacion encontrada = template.execute(status ->
                organizacionRepository.findConBloqueoById(organizacion.getId()).orElseThrow());

        assertThat(encontrada.getId()).isEqualTo(organizacion.getId());
        assertThat(encontrada.getNombre()).isEqualTo(organizacion.getNombre());
    }

    @Test
    @Timeout(20)
    @DisplayName("El lock bloquea una segunda transacción hasta que la primera termina")
    void elLockBloqueaUnaSegundaTransaccionConcurrente() throws Exception {
        Organizacion organizacion = crearOrganizacion();
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        CountDownLatch lockAdquirido = new CountDownLatch(1);
        CountDownLatch liberarPrimeraTransaccion = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Hilo A: adquiere el lock vía el método real del repositorio y lo mantiene abierto
            // (transacción sin terminar) hasta que el test le da la señal de liberar.
            Future<?> tenedorDelLock = executor.submit(() -> template.execute(status -> {
                organizacionRepository.findConBloqueoById(organizacion.getId());
                lockAdquirido.countDown();
                try {
                    liberarPrimeraTransaccion.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));

            assertThat(lockAdquirido.await(5, TimeUnit.SECONDS)).isTrue();

            // Hilo B: intenta adquirir el MISMO lock (mismo método, misma fila) mientras A sigue
            // con la transacción abierta.
            Future<Void> segundoIntento = executor.submit(() -> {
                template.execute(status -> organizacionRepository.findConBloqueoById(organizacion.getId()));
                return null;
            });

            // Todavía bloqueado: no debe completar dentro de un deadline corto mientras A no liberó.
            assertThrows(TimeoutException.class, () -> segundoIntento.get(300, TimeUnit.MILLISECONDS));

            liberarPrimeraTransaccion.countDown();
            assertDoesNotThrow(() -> tenedorDelLock.get(5, TimeUnit.SECONDS));

            // Liberado: ahora sí debe completar, holgadamente dentro del LOCK_TIMEOUT configurado.
            assertDoesNotThrow(() -> segundoIntento.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Organizacion crearOrganizacion() {
        Usuario creador = usuarioRepository.save(Usuario.builder()
                .email("creador-" + UUID.randomUUID() + "@organizacion.test")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Creador")
                .rol(RolUsuario.TERAPEUTA)
                .build());
        return organizacionRepository.save(Organizacion.builder()
                .nombre("Org " + UUID.randomUUID())
                .creadoPor(creador)
                .build());
    }
}
