package com.caa.api.dtos;

import com.caa.api.models.PermisoColaborador;
import java.time.LocalDateTime;
import java.util.UUID;

public record ColaboradorResponseDTO(
        UUID usuarioId,
        String nombre,
        String email,
        PermisoColaborador permiso,
        LocalDateTime vinculadoEn
) {
}
