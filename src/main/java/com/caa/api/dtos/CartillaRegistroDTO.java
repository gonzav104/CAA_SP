package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

public record CartillaRegistroDTO(
        @NotBlank(message = "El nombre de la cartilla es obligatorio") String nombre,
        Boolean esPrincipal
) {
}
