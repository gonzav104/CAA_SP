package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String email,
        String nombre,
        LocalDateTime creadoEn
) {
}
