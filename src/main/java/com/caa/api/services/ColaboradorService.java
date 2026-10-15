package com.caa.api.services;

import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import java.util.List;
import java.util.UUID;

public interface ColaboradorService {
    /**
     * Ahora crea una invitación familiar (spec {@code patient-collaborators} MODIFICADA): el
     * email destino no necesita tener cuenta todavía. Nada se concede hasta que la invitación
     * se acepta.
     */
    InvitacionResponseDTO vincularColaborador(UUID pacienteId, ColaboradorRegistroDTO dto, String email);

    List<ColaboradorResponseDTO> obtenerColaboradores(UUID pacienteId, String email);

    ColaboradorResponseDTO actualizarPermiso(UUID pacienteId, UUID usuarioId, ColaboradorActualizacionDTO dto, String email);

    void revocarColaborador(UUID pacienteId, UUID usuarioId, String email);
}
