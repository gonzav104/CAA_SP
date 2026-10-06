package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ItemCartillaRegistroDTO(
        @NotBlank(message = "El texto hablado es obligatorio")
        String textoHablado,
        Integer ordenVisual,
        UUID recursoGlobalId,
        UUID recursoCustomId,
        Boolean esCore,
        @Size(max = 30, message = "El texto visible no puede superar los 30 caracteres")
        @Pattern(regexp = ".*\\S.*", message = "El texto visible no puede estar vacío")
        String textoVisible,
        Boolean visibleEnModoUso
) {
}
