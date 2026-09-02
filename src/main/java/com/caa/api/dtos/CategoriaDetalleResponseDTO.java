package com.caa.api.dtos;

import java.util.List;
import java.util.UUID;

public record CategoriaDetalleResponseDTO(
        UUID id,
        String nombre,
        String colorHex,
        Integer orden,
        List<ItemDetalleResponseDTO> items
) {
}
