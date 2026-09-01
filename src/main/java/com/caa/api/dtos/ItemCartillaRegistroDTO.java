package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ItemCartillaRegistroDTO(
        @NotBlank(message = "El texto hablado es obligatorio")
        String textoHablado,
        Integer ordenVisual,
        UUID recursoGlobalId,
        UUID recursoCustomId
) {
}
