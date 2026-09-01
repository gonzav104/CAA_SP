package com.caa.api.services;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import java.util.List;
import java.util.UUID;

public interface CartillaService {
    CartillaResponseDTO crearCartilla(UUID pacienteId, CartillaRegistroDTO dto, String emailTerapeuta);
    List<CartillaResponseDTO> obtenerCartillasDePaciente(UUID pacienteId, String emailTerapeuta);
    CartillaResponseDTO actualizarCartilla(UUID pacienteId, UUID cartillaId, CartillaActualizacionDTO dto, String emailTerapeuta);
    void eliminarCartilla(UUID pacienteId, UUID cartillaId, String emailTerapeuta);
}
