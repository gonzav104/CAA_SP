package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record PictogramaCustomResponseDTO(
        UUID id,
        UUID pacienteId,
        String etiqueta,
        String imagenUrl,
        LocalDateTime creadoEn
) {
}