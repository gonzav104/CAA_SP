package com.caa.api.dtos;

/**
 * Resultado de una materialización de pictograma ARASAAC.
 *
 * <p>{@code recienCreado} distingue el caso 201 (pictograma insertado por primera
 * vez) del caso 200 (dedupe hit: el {@code arasaacId} ya existía y se devuelve el
 * registro existente, idempotente).
 *
 * @param pictograma   el pictograma global materializado (nuevo o existente)
 * @param recienCreado {@code true} si se insertó ahora, {@code false} si es un dedupe hit
 */
public record MaterializarResultadoDTO(
        PictogramaGlobalResponseDTO pictograma,
        boolean recienCreado
) {
}