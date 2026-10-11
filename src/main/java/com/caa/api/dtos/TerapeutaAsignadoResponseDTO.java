package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code TerapeutaAsignadoResponseDTO} (design-part2 §13): terapeuta con asignación clínica
 * ({@code PacienteTerapeuta}) vigente sobre un paciente.
 */
public record TerapeutaAsignadoResponseDTO(
        UUID usuarioId,
        String nombre,
        String email,
        LocalDateTime asignadoEn
) {
}
