package com.caa.api.dtos;

import com.caa.api.models.RolUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UsuarioRegistroDTO(
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(
                // Símbolo = caracter que no es letra ni número (en cualquier idioma).
                // Con [^A-Za-z0-9] la ñ contaría como "símbolo", lo cual es incorrecto.
                // Se usa \p{L} y \p{N} (Unicode-aware) para coincidir con el frontend.
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^\\p{L}\\p{N}]).{8,}$",
                message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo"
        )
        String password,
        @NotBlank String nombre,
        @NotNull RolUsuario rol
) {
}
