package com.caa.api.dtos;

import java.util.UUID;

public record ItemDetalleResponseDTO(
        UUID id,
        String textoHablado,
        Integer ordenVisual,
        PictogramaInfoDTO pictograma
) {
}
