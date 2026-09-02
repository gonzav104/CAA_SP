package com.caa.api.dtos;

import java.util.List;
import java.util.UUID;

public record CartillaDetalleResponseDTO(
        UUID id,
        String nombre,
        Boolean esPrincipal,
        List<CategoriaDetalleResponseDTO> categorias
) {
}
