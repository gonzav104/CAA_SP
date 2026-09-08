package com.caa.api.services;

import com.caa.api.exceptions.DemasiadosIntentosException;
import com.caa.api.services.RateLimitService.Operacion;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RateLimitService — Tests unitarios del token bucket")
class RateLimitServiceTest {

    private static final String EMAIL = "test@ejemplo.com";

    private RateLimitTimeMeter timeMeter;
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        timeMeter = new RateLimitTimeMeter();
        // Mismos defaults de producción: login max 5 / olvide-password max 3, ventana 15 min
        rateLimitService = new RateLimitService(true, 5, 15, 3, 15, timeMeter);
    }

    @Test
    @DisplayName("olvide-password: los primeros 3 intentos pasan, el 4to lanza DemasiadosIntentosException")
    void olvidePassword_cuatroIntento_lanzaExcepcion() {
        for (int i = 0; i < 3; i++) {
            rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL);
        }

        assertThatThrownBy(() -> rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    @DisplayName("login: los primeros 5 intentos pasan, el 6to lanza DemasiadosIntentosException")
    void login_sextoIntento_lanzaExcepcion() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.verificarYConsumir(Operacion.LOGIN, EMAIL);
        }

        assertThatThrownBy(() -> rateLimitService.verificarYConsumir(Operacion.LOGIN, EMAIL))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    @DisplayName("Pasada la ventana de tiempo, el cupo se repone y vuelve a funcionar")
    void pasadoElTiempo_vuelveAFuncionar() {
        for (int i = 0; i < 3; i++) {
            rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL);
        }
        assertThatThrownBy(() -> rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL))
                .isInstanceOf(DemasiadosIntentosException.class);

        // Simula el paso de la ventana completa (15 min) + margen
        timeMeter.avanzar(Duration.ofMinutes(16));

        // El cupo se repuso: vuelve a aceptar intentos
        assertThatCode(() -> rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("login y olvide-password NO comparten cupo para el mismo email")
    void loginYOlvidePassword_noCompartenCupo() {
        // Consumo TODO el cupo de login
        for (int i = 0; i < 5; i++) {
            rateLimitService.verificarYConsumir(Operacion.LOGIN, EMAIL);
        }
        assertThatThrownBy(() -> rateLimitService.verificarYConsumir(Operacion.LOGIN, EMAIL))
                .isInstanceOf(DemasiadosIntentosException.class);

        // El cupo de olvide-password sigue intacto (clave separada por operación)
        assertThatCode(() -> rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Emails distintos tienen buckets independientes")
    void emailsDistintos_tienenBucketsIndependientes() {
        for (int i = 0; i < 3; i++) {
            rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL);
        }
        assertThatThrownBy(() -> rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL))
                .isInstanceOf(DemasiadosIntentosException.class);

        assertThatCode(() -> rateLimitService.verificarYConsumir(Operacion.OLVIDE_PASSWORD, "otro@ejemplo.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Con el rate limit deshabilitado, nunca lanza aunque se supere cualquier límite")
    void deshabilitado_nuncaLanza() {
        RateLimitService deshabilitado = new RateLimitService(false, 1, 15, 1, 15, timeMeter);

        for (int i = 0; i < 100; i++) {
            assertThatCode(() -> deshabilitado.verificarYConsumir(Operacion.LOGIN, EMAIL))
                    .doesNotThrowAnyException();
            assertThatCode(() -> deshabilitado.verificarYConsumir(Operacion.OLVIDE_PASSWORD, EMAIL))
                    .doesNotThrowAnyException();
        }
    }
}