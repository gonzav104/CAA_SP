package com.caa.api.services;

import com.caa.api.dtos.SesionRegistroDTO;
import com.caa.api.dtos.SesionResponseDTO;
import java.util.List;
import java.util.UUID;

public interface SesionService {
    SesionResponseDTO registrarSesion(UUID pacienteId, SesionRegistroDTO dto, String emailTerapeuta);
    List<SesionResponseDTO> obtenerSesionesDePaciente(UUID pacienteId, String emailTerapeuta);
}
