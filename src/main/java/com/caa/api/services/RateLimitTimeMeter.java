package com.caa.api.services;

import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * {@link TimeMeter} de Bucket4j con reloj AVANCEABLE, usado por
 * {@link RateLimitService} para construir los buckets.
 * <p>
 * En producción se comporta como el reloj del sistema (misma base que
 * {@code TimeMeter.SYSTEM_NANOTIME}). Los tests pueden adelantar el tiempo vía
 * {@link #avanzar(Duration)} para verificar que la ventana del rate limit
 * expira sin esperar minutos reales.
 */
@Component
public class RateLimitTimeMeter implements TimeMeter {

    private final AtomicLong offsetNanos = new AtomicLong(0);

    @Override
    public long currentTimeNanos() {
        return System.nanoTime() + offsetNanos.get();
    }

    @Override
    public boolean isWallClockBased() {
        return false;
    }

    /**
     * Adelanta el reloj la duración indicada (simula el paso del tiempo).
     * Solo para tests.
     */
    public void avanzar(Duration duracion) {
        offsetNanos.addAndGet(duracion.toNanos());
    }

    /**
     * Vuelve el reloj a tiempo real. Solo para tests.
     */
    public void reiniciar() {
        offsetNanos.set(0);
    }
}