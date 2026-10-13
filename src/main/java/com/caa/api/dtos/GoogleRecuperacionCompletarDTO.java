package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Both independent proofs and a replacement local password are mandatory. */
public record GoogleRecuperacionCompletarDTO(
        @NotBlank String idToken,
        @NotBlank String token,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^\\p{L}\\p{N}]).{8,}$",
                message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo")
        String password
) {
}
