package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.regex.Pattern;

public record CategoriaRegistroDTO(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        String nombre,
        @NotBlank(message = "El color es obligatorio")
        @Size(min = 7, max = 7, message = "El color debe estar en formato hex (7 caracteres, ej: #E0E0E0)")
        String colorHex,
        Integer orden
) {
    // Validación opcional de formato hex en el constructor del record
    public CategoriaRegistroDTO {
        if (colorHex != null && !Pattern.matches("^#[0-9A-Fa-f]{6}$", colorHex)) {
            throw new IllegalArgumentException("El color debe estar en formato hex, ej: #E0E0E0");
        }
    }
}
