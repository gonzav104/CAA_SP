package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record CartillaResponseDTO(
        UUID id,
        UUID pacienteId,
        String nombre,
        Boolean esPrincipal,
        LocalDateTime creadoEn
) {
}
