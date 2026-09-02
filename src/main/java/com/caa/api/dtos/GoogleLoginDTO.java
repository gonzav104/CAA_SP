package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginDTO(
        @NotBlank(message = "El idToken de Google es obligatorio")
        String idToken
) {
}
