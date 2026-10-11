package com.caa.api.dtos;

import com.caa.api.models.PermisoColaborador;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PacienteResponseDTO(
        UUID id,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento,
        LocalDateTime creadoEn,
        PermisoColaborador miPermiso,
        Integer gridSize,
        UUID organizacionId
) {
}
