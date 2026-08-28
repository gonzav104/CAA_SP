package com.caa.api.dtos;

import com.caa.api.models.RolUsuario;
import java.time.LocalDateTime;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String email,
        String nombre,
        RolUsuario rol,
        LocalDateTime creadoEn
) {
}
