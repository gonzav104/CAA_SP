package com.caa.api.dtos;

import com.caa.api.models.RolGestion;
import java.time.LocalDateTime;
import java.util.UUID;

/** design §13: {@code MiembroResponseDTO{usuarioId, nombre, email, rolGestion, esTerapeuta, unidoEn}}. */
public record MiembroResponseDTO(
        UUID usuarioId,
        String nombre,
        String email,
        RolGestion rolGestion,
        boolean esTerapeuta,
        LocalDateTime unidoEn
) {
}
