package com.caa.api.dtos;

import com.caa.api.models.TipoInvitacion;
import java.util.UUID;

/**
 * Respuesta de aceptación (design §9.4): nunca incluye el token. Exactamente uno de
 * {@code organizacionId}/{@code pacienteId} está presente, según {@code tipo}.
 */
public record AceptarInvitacionResponseDTO(
        TipoInvitacion tipo,
        UUID organizacionId,
        UUID pacienteId
) {
}
