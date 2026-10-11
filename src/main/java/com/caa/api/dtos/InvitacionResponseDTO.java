package com.caa.api.dtos;

import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.TipoInvitacion;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Respuesta uniforme de creación/listado de invitaciones (design §9.3): idéntica en forma sea el
 * email destino ya tenga cuenta o no (no-enumeración). NUNCA incluye el token crudo ni su hash.
 * Los campos de organización son {@code null} para invitaciones familiares y viceversa.
 */
public record InvitacionResponseDTO(
        UUID id,
        TipoInvitacion tipo,
        String email,
        RolGestion rolGestionPropuesto,
        Boolean esTerapeutaPropuesto,
        PermisoColaborador permisoPropuesto,
        EstadoInvitacion estado,
        LocalDateTime expiraEn
) {
}
