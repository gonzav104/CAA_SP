package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record PictogramaGlobalResponseDTO(
        UUID id,
        String etiqueta,
        String imagenUrl,
        LocalDateTime creadoEn
) {
}
