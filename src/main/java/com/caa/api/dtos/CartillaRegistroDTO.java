package com.caa.api.dtos;

import com.caa.api.models.ParadigmaCartilla;
import jakarta.validation.constraints.NotBlank;

public record CartillaRegistroDTO(
        @NotBlank(message = "El nombre de la cartilla es obligatorio") String nombre,
        Boolean esPrincipal,
        ParadigmaCartilla paradigma
) {
}
