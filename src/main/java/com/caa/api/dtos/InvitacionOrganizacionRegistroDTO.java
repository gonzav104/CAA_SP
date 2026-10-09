package com.caa.api.dtos;

import com.caa.api.models.RolGestion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/organizaciones/{organizacionId}/invitaciones} (design §9.3, §12.2).
 * {@code rolGestion} y {@code esTerapeuta} son dos campos independientes — nunca un único "rol".
 * {@code OWNER} nunca es un valor válido aquí (validado en el servicio contra los derechos del
 * invitador, y reforzado por el CHECK {@code chk_invitacion_contexto} en init.sql).
 */
public record InvitacionOrganizacionRegistroDTO(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        String email,

        @NotNull(message = "El rol de gestión propuesto es obligatorio")
        RolGestion rolGestion,

        @NotNull(message = "La capacidad clínica propuesta es obligatoria")
        Boolean esTerapeuta
) {
}
