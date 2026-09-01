package com.caa.api.dtos;

import com.caa.api.models.PermisoColaborador;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ColaboradorRegistroDTO(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        String email,

        @NotNull(message = "El permiso es obligatorio")
        PermisoColaborador permiso
) {
}
