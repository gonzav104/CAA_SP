package com.caa.api.dtos;

import com.caa.api.models.RolGestion;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/organizaciones/{organizacionId}/miembros/{usuarioId}/rol-gestion} (design §12.2):
 * solo OWNER puede invocar este endpoint y el valor propuesto debe ser ADMIN o MIEMBRO — nunca
 * OWNER (design part 1 "Server-Side-Only Assignment of rolGestion and esTerapeuta"; "no body ever
 * carries OWNER"). El rechazo de OWNER se valida en el servicio, no aquí, para devolver siempre el
 * mismo 400 ya mapeado por {@code GlobalExceptionHandler.handleIllegalArgument}.
 */
public record MiembroRolGestionDTO(
        @NotNull(message = "El rol de gestión es obligatorio") RolGestion rolGestion
) {
}
