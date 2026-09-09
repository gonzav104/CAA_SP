package com.caa.api.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MaterializarPictogramaDTO(
        @Min(value = 1, message = "arasaacId debe ser un número positivo")
        Long arasaacId,

        @NotBlank(message = "etiqueta no puede estar vacía")
        String etiqueta
) {
}
