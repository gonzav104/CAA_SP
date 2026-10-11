package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

/**
 * Completar registro de identidad únicamente (design-part2 §15 stage 6, cutover; spec {@code
 * user-registration} MODIFIED). NUNCA incluye un campo de rol: completar el registro no otorga
 * ningún privilegio de organización.
 */
public record GoogleCompletarRegistroDTO(
        @NotBlank(message = "El idToken de Google es obligatorio")
        String idToken
) {
}