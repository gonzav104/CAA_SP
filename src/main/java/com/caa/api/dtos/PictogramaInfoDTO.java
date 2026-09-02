package com.caa.api.dtos;

import java.util.UUID;

public record PictogramaInfoDTO(
        UUID id,
        String etiqueta,
        String imagenUrl,
        String tipo
) {
}
