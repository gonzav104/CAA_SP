package com.caa.api.services;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaDetalleResponseDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import java.util.List;
import java.util.UUID;

public interface CartillaService {
    CartillaResponseDTO crearCartilla(UUID pacienteId, CartillaRegistroDTO dto, String email);
    List<CartillaResponseDTO> obtenerCartillasDePaciente(UUID pacienteId, String email);
    CartillaDetalleResponseDTO obtenerCartillaDetalle(UUID pacienteId, UUID cartillaId, String email);
    CartillaResponseDTO actualizarCartilla(UUID pacienteId, UUID cartillaId, CartillaActualizacionDTO dto, String email);
    CartillaResponseDTO establecerCartillaPrincipal(UUID pacienteId, UUID cartillaId, String email);
    void eliminarCartilla(UUID pacienteId, UUID cartillaId, String email);
}
