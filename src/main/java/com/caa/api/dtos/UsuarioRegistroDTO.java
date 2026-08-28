package com.caa.api.dtos;

import com.caa.api.models.RolUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioRegistroDTO(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String nombre,
        @NotNull RolUsuario rol
) {
}
