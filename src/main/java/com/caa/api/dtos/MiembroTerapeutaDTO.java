package com.caa.api.dtos;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/organizaciones/{organizacionId}/miembros/{usuarioId}/terapeuta} (design §12.2):
 * toggle de la capacidad clínica, independiente de {@code rolGestion}.
 */
public record MiembroTerapeutaDTO(
        @NotNull(message = "El valor de esTerapeuta es obligatorio") Boolean esTerapeuta
) {
}
