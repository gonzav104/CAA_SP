package com.caa.api.dtos;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /api/pacientes/{pacienteId}/terapeutas} (design-part2 §12.2, §13): asigna como
 * terapeuta clínico a un miembro de la MISMA organización del paciente con {@code esTerapeuta=true}.
 */
public record AsignacionTerapeutaDTO(
        @NotNull(message = "El usuario a asignar es obligatorio") UUID usuarioId
) {
}
