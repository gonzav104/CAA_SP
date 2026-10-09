package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

/** {@code PUT /api/organizaciones/{organizacionId}} (design §12.2): renombrar. */
public record OrganizacionActualizacionDTO(
        @NotBlank(message = "El nombre de la organización es obligatorio") String nombre
) {
}
