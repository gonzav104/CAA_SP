package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/organizaciones} (design §12.2, part 1 §10.1). {@code esTerapeuta} es opcional;
 * si se omite, el servicio aplica el default {@code true} (design part 1 "Organization Creation
 * Is Atomic With Owner Assignment").
 */
public record OrganizacionRegistroDTO(
        @NotBlank(message = "El nombre de la organización es obligatorio") String nombre,
        Boolean esTerapeuta
) {
}
