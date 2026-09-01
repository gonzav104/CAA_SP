package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoriaResponseDTO(
        UUID id,
        UUID cartillaId,
        String nombre,
        String colorHex,
        Integer orden,
        LocalDateTime creadoEn
) {
}
