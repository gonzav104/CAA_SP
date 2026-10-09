package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/invitaciones/aceptar} (design §9.4). */
public record AceptarInvitacionDTO(
        @NotBlank(message = "El token es obligatorio")
        String token
) {
}
