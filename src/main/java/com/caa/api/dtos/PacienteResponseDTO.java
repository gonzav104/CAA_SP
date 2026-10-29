package com.caa.api.dtos;

import com.caa.api.models.PermisoColaborador;
import com.caa.api.services.AccesoService.Capacidad;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * {@code capacidades}: capacidades efectivas del usuario autenticado sobre el paciente
 * ({@code EDITAR_CONTENIDO}, {@code GESTION_CLINICA}, {@code ADMINISTRAR}), derivadas de
 * {@code AccesoPaciente#capacidadesEfectivas()}. Es una pista para la UI: el backend sigue
 * validando cada operación.
 */
public record PacienteResponseDTO(
        UUID id,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento,
        LocalDateTime creadoEn,
        PermisoColaborador miPermiso,
        Integer gridSize,
        UUID organizacionId,
        Set<Capacidad> capacidades
) {
}
