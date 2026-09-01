package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record ItemCartillaResponseDTO(
        UUID id,
        UUID categoriaId,
        String textoHablado,
        Integer ordenVisual,
        UUID recursoGlobalId,
        UUID recursoCustomId,
        LocalDateTime creadoEn
) {
}
