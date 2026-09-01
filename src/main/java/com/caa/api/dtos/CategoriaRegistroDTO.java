package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CategoriaRegistroDTO(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        String nombre,
        @NotBlank(message = "El color es obligatorio")
        @Pattern(
                regexp = "^#[0-9A-Fa-f]{6}$",
                message = "El color debe estar en formato hex, ej: #E0E0E0"
        )
        String colorHex,
        Integer orden
) {
}
