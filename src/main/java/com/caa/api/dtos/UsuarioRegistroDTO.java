package com.caa.api.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Registro de identidad únicamente (design-part2 §15 stage 6, cutover; spec {@code
 * user-registration} MODIFIED). NUNCA incluye un campo de rol: el registro no otorga ningún
 * privilegio de organización. Si un cliente llegara a mandar un campo {@code rol} legacy en el
 * JSON crudo, Jackson lo ignora (no hay propiedad {@code rol} en este record para enlazarlo).
 */
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
        @NotBlank String nombre
) {
}
