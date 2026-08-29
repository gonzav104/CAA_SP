package com.caa.api.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PacienteResponseDTO(
        UUID id,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento,
        LocalDateTime creadoEn
) {
}
