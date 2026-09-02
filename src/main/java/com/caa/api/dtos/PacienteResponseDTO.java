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
        PermisoColaborador miPermiso
) {

    // Constructor compatible con los usos previos (lista, registro, actualización):
    // en estos casos no se resuelve el permiso del usuario autenticado → miPermiso null.
    public PacienteResponseDTO(
            UUID id,
            String nombre,
            String apellido,
            LocalDate fechaNacimiento,
            LocalDateTime creadoEn) {
        this(id, nombre, apellido, fechaNacimiento, creadoEn, null);
    }
}
