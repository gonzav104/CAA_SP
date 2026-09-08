package com.caa.api.services;

import com.caa.api.exceptions.DemasiadosIntentosException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Rate limit en memoria (single-node) con token bucket, para operaciones
 * sensibles por email: login y recupero de contraseña.
 * <p>
 * Clave de bucket separada por (operación, email): login y olvide-password NO
 * comparten cupo. El chequeo se hace SIEMPRE antes de validar credenciales o
 * existencia del email (anti fuerza bruta): un intento fallido consume cupo, y
 * también uno exitoso.
 * <p>
 * Configurable vía properties:
 * <ul>
 *   <li>{@code app.rate-limit.enabled} (default true) — flag para deshabilitar
 *       en tests de integración que no testean rate limit.</li>
 *   <li>{@code app.rate-limit.login.max} (default 5) y
 *       {@code app.rate-limit.login.window-minutes} (default 15).</li>
 *   <li>{@code app.rate-limit.olvide-password.max} (default 3) y
 *       {@code app.rate-limit.olvide-password.window-minutes} (default 15).</li>
 * </ul>
 * <p>
 * El reloj es inyectable ({@link RateLimitTimeMeter}): los tests adelantan el
 * tiempo para verificar que la ventana expira sin esperar minutos reales.
 */
@Service
public class RateLimitService {

    public enum Operacion {
        LOGIN,
        OLVIDE_PASSWORD
    }

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int loginMax;
    private final long loginWindowMinutos;
    private final int olvidePasswordMax;
    private final long olvidePasswordWindowMinutos;
    private final RateLimitTimeMeter timeMeter;

    public RateLimitService(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.login.max:5}") int loginMax,
            @Value("${app.rate-limit.login.window-minutes:15}") long loginWindowMinutos,
            @Value("${app.rate-limit.olvide-password.max:3}") int olvidePasswordMax,
            @Value("${app.rate-limit.olvide-password.window-minutes:15}") long olvidePasswordWindowMinutos,
            RateLimitTimeMeter timeMeter) {
        this.enabled = enabled;
        this.loginMax = loginMax;
        this.loginWindowMinutos = loginWindowMinutos;
        this.olvidePasswordMax = olvidePasswordMax;
        this.olvidePasswordWindowMinutos = olvidePasswordWindowMinutos;
        this.timeMeter = timeMeter;
    }

    /**
     * Chequea el cupo de la operación para el email y consume UN intento.
     * Si el cupo está agotado lanza {@link DemasiadosIntentosException}
     * (HTTP 429 con mensaje genérico y uniforme).
     */
    public void verificarYConsumir(Operacion operacion, String email) {
        if (!enabled) {
            return;
        }
        Bucket bucket = buckets.computeIfAbsent(clave(operacion, email), k -> crearBucket(operacion));
        if (!bucket.tryConsume(1)) {
            throw new DemasiadosIntentosException();
        }
    }

    private String clave(Operacion operacion, String email) {
        return operacion.name() + ":" + email;
    }

    private Bucket crearBucket(Operacion operacion) {
        int max = operacion == Operacion.LOGIN ? loginMax : olvidePasswordMax;
        long windowMinutos = operacion == Operacion.LOGIN ? loginWindowMinutos : olvidePasswordWindowMinutos;
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(Bandwidth.classic(max, Refill.intervally(max, Duration.ofMinutes(windowMinutos))))
                .build();
    }
}