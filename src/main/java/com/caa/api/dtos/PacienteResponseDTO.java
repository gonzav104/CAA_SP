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

    // Constructores compatibles con los usos previos (lista, registro, actualización):
    // en estos casos no se resuelve el permiso del usuario autenticado → miPermiso null,
    // gridSize se completa explícitamente donde corresponda, y organizacionId queda null
    // (uso legacy: el endpoint deprecado POST /api/pacientes nunca conoce una organización).
    public PacienteResponseDTO(
            UUID id,
            String nombre,
            String apellido,
            LocalDate fechaNacimiento,
            LocalDateTime creadoEn) {
        this(id, nombre, apellido, fechaNacimiento, creadoEn, null, null, null);
    }

    public PacienteResponseDTO(
            UUID id,
            String nombre,
            String apellido,
            LocalDate fechaNacimiento,
            LocalDateTime creadoEn,
            PermisoColaborador miPermiso) {
        this(id, nombre, apellido, fechaNacimiento, creadoEn, miPermiso, null, null);
    }

    public PacienteResponseDTO(
            UUID id,
            String nombre,
            String apellido,
            LocalDate fechaNacimiento,
            LocalDateTime creadoEn,
            PermisoColaborador miPermiso,
            Integer gridSize) {
        this(id, nombre, apellido, fechaNacimiento, creadoEn, miPermiso, gridSize, null);
    }
}
