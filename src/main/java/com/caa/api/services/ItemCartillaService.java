package com.caa.api.services;

import com.caa.api.dtos.ItemCartillaActualizacionDTO;
import com.caa.api.dtos.ItemCartillaRegistroDTO;
import com.caa.api.dtos.ItemCartillaResponseDTO;
import java.util.List;
import java.util.UUID;

public interface ItemCartillaService {
    ItemCartillaResponseDTO crearItem(UUID pacienteId, UUID cartillaId, UUID categoriaId, ItemCartillaRegistroDTO dto, String emailTerapeuta);
    List<ItemCartillaResponseDTO> obtenerItemsDeCategoria(UUID pacienteId, UUID cartillaId, UUID categoriaId, String emailTerapeuta);
    ItemCartillaResponseDTO actualizarItem(UUID pacienteId, UUID cartillaId, UUID categoriaId, UUID itemId, ItemCartillaActualizacionDTO dto, String emailTerapeuta);
    void eliminarItem(UUID pacienteId, UUID cartillaId, UUID categoriaId, UUID itemId, String emailTerapeuta);
}
