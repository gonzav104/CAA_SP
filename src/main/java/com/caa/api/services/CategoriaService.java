package com.caa.api.services;

import com.caa.api.dtos.CategoriaActualizacionDTO;
import com.caa.api.dtos.CategoriaRegistroDTO;
import com.caa.api.dtos.CategoriaResponseDTO;
import java.util.List;
import java.util.UUID;

public interface CategoriaService {
    CategoriaResponseDTO crearCategoria(UUID pacienteId, UUID cartillaId, CategoriaRegistroDTO dto, String emailTerapeuta);
    List<CategoriaResponseDTO> obtenerCategoriasDeCartilla(UUID pacienteId, UUID cartillaId, String emailTerapeuta);
    CategoriaResponseDTO actualizarCategoria(UUID pacienteId, UUID cartillaId, UUID categoriaId, CategoriaActualizacionDTO dto, String emailTerapeuta);
    void eliminarCategoria(UUID pacienteId, UUID cartillaId, UUID categoriaId, String emailTerapeuta);
}
