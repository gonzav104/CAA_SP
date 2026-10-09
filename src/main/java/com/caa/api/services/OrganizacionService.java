package com.caa.api.services;

import com.caa.api.dtos.MiembroResponseDTO;
import com.caa.api.dtos.MiembroRolGestionDTO;
import com.caa.api.dtos.MiembroTerapeutaDTO;
import com.caa.api.dtos.OrganizacionActualizacionDTO;
import com.caa.api.dtos.OrganizacionRegistroDTO;
import com.caa.api.dtos.OrganizacionResponseDTO;
import com.caa.api.dtos.TransferenciaPropiedadDTO;
import java.util.List;
import java.util.UUID;

/**
 * CRUD de {@code Organizacion} y gestión de {@code Membresia} (design §12.2, design-part2 §13,
 * spec {@code organization-membership}). Toda operación que muta membresías adquiere primero el
 * bloqueo exclusivo de fila sobre la organización ({@code OrganizacionRepository.findConBloqueoById},
 * design §3.3 punto 3).
 */
public interface OrganizacionService {

    /**
     * {@code POST /api/organizaciones}: crea la {@code Organizacion} y su {@code Membresia}
     * (rolGestion=OWNER) en una sola transacción. {@code dto.esTerapeuta()} nulo → default true.
     */
    OrganizacionResponseDTO crear(OrganizacionRegistroDTO dto, String email);

    /** {@code GET /api/organizaciones}: organizaciones donde el usuario tiene una Membresia. */
    List<OrganizacionResponseDTO> listarMisOrganizaciones(String email);

    /** {@code GET /api/organizaciones/{organizacionId}}: exige membresía (cualquier rolGestion). */
    OrganizacionResponseDTO obtener(UUID organizacionId, String email);

    /** {@code PUT /api/organizaciones/{organizacionId}}: renombrar, rolGestion OWNER o ADMIN. */
    OrganizacionResponseDTO renombrar(UUID organizacionId, OrganizacionActualizacionDTO dto, String email);

    /**
     * {@code DELETE /api/organizaciones/{organizacionId}}: rolGestion OWNER únicamente; bloqueado
     * (409) mientras existan pacientes en la organización.
     */
    void eliminar(UUID organizacionId, String email);

    /** {@code GET /api/organizaciones/{organizacionId}/miembros}: cualquier miembro. */
    List<MiembroResponseDTO> listarMiembros(UUID organizacionId, String email);

    /**
     * {@code PUT .../miembros/{usuarioId}/rol-gestion}: solo OWNER; el objetivo nunca puede ser la
     * propia fila del OWNER; el valor propuesto nunca puede ser OWNER.
     */
    MiembroResponseDTO cambiarRolGestion(UUID organizacionId, UUID usuarioId, MiembroRolGestionDTO dto,
                                          String email);

    /**
     * {@code PUT .../miembros/{usuarioId}/terapeuta}: OWNER sobre cualquier miembro (incl. sí
     * mismo); ADMIN sobre cualquier miembro excepto el OWNER. {@code false} elimina en bloque las
     * asignaciones {@code PacienteTerapeuta} de ese miembro en esta organización (design §3.5).
     */
    MiembroResponseDTO cambiarEsTerapeuta(UUID organizacionId, UUID usuarioId, MiembroTerapeutaDTO dto,
                                           String email);

    /**
     * {@code DELETE .../miembros/{usuarioId}}: baja por OWNER (cualquier no-OWNER) o ADMIN (solo
     * MIEMBRO), o salida voluntaria del propio usuario. El OWNER no puede ser eliminado ni puede
     * salir sin transferir la propiedad primero (409).
     */
    void eliminarMiembro(UUID organizacionId, UUID usuarioId, String email);

    /**
     * {@code POST .../transferencia-propiedad}: solo OWNER; el destino debe ser miembro activo de
     * la MISMA organización; degrada al OWNER actual a ADMIN y promueve al destino a OWNER en una
     * transacción, sin alterar {@code esTerapeuta} de ninguna de las dos membresías.
     */
    void transferirPropiedad(UUID organizacionId, TransferenciaPropiedadDTO dto, String email);
}
