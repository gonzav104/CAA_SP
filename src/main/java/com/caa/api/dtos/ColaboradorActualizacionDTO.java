package com.caa.api.dtos;

import com.caa.api.models.PermisoColaborador;
import jakarta.validation.constraints.NotNull;

public record ColaboradorActualizacionDTO(
        @NotNull(message = "El permiso es obligatorio")
        PermisoColaborador permiso
) {
}
