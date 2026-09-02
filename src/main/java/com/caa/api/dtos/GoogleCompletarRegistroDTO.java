package com.caa.api.dtos;

import com.caa.api.models.RolUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoogleCompletarRegistroDTO(
        @NotBlank(message = "El idToken de Google es obligatorio")
        String idToken,
        @NotNull(message = "El rol es obligatorio")
        RolUsuario rol
) {
}