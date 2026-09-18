package com.caa.api.dtos;

import com.caa.api.models.ParadigmaCartilla;
import java.util.List;
import java.util.UUID;

public record CartillaDetalleResponseDTO(
        UUID id,
        UUID creadorId,
        String nombre,
        Boolean esPrincipal,
        ParadigmaCartilla paradigma,
        List<CategoriaDetalleResponseDTO> categorias
) {
}
