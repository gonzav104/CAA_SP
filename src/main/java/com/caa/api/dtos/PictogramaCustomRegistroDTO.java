package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

public record PictogramaCustomRegistroDTO(
        @NotBlank(message = "La etiqueta es obligatoria")
        String etiqueta
) {
}