package com.caa.api.services;

import com.caa.api.dtos.AsignacionTerapeutaDTO;
import com.caa.api.dtos.TerapeutaAsignadoResponseDTO;
import java.util.List;
import java.util.UUID;

/**
 * Asignación clínica terapeuta↔paciente vía {@code PacienteTerapeuta} (design-part2 §12.2, §13;
 * spec {@code patient-therapist-assignment}). Separado de {@code OrganizacionService} (que
 * gestiona la {@code Membresia}, es decir, la pertenencia a la organización y el atributo
 * {@code esTerapeuta}): este servicio solo gestiona QUÉ pacientes tiene asignados cada terapeuta.
 */
public interface PacienteTerapeutaService {

    /**
     * {@code GET /api/pacientes/{pacienteId}/terapeutas}: exige {@code Capacidad.GESTION_CLINICA}
     * (equivale a {@code acceso.esEquipo()} en la matriz de {@code AccesoServiceImpl} — cualquier
     * miembro de gestión o clínico ya asignado puede ver el roster).
     */
    List<TerapeutaAsignadoResponseDTO> listarAsignados(UUID pacienteId, String email);

    /**
     * {@code POST /api/pacientes/{pacienteId}/terapeutas}: exige {@code Capacidad.ADMINISTRAR}
     * (rolGestion OWNER/ADMIN del llamante, independiente de su propio {@code esTerapeuta}). El
     * objetivo debe ser miembro de la MISMA organización del paciente (si no → 404 genérico) y
     * tener {@code esTerapeuta=true} en esa membresía (si no → 409 {@code ConflictoException}).
     * Asignación duplicada es idempotente: no crea una segunda fila.
     */
    TerapeutaAsignadoResponseDTO asignar(UUID pacienteId, AsignacionTerapeutaDTO dto, String email);

    /**
     * {@code DELETE /api/pacientes/{pacienteId}/terapeutas/{usuarioId}}: exige
     * {@code Capacidad.ADMINISTRAR}. Puede dejar al paciente sin terapeutas asignados (estado
     * válido); desasignar una fila que ya no existe es un no-op (idempotente).
     */
    void desasignar(UUID pacienteId, UUID usuarioId, String email);
}
