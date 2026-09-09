package com.caa.api.services;

import com.caa.api.dtos.MaterializarPictogramaDTO;
import com.caa.api.dtos.MaterializarResultadoDTO;
import com.caa.api.dtos.PictogramaGlobalResponseDTO;
import java.util.List;
import java.util.UUID;

public interface PictogramaGlobalService {
    List<PictogramaGlobalResponseDTO> obtenerTodos();
    PictogramaGlobalResponseDTO obtenerPorId(UUID id);
    MaterializarResultadoDTO materializar(MaterializarPictogramaDTO dto);
}
