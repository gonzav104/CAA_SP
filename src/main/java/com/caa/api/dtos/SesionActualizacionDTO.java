package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record SesionActualizacionDTO(
        @NotNull(message = "La fecha y hora es obligatoria")
        LocalDateTime fechaHora,
        String disposicion,
        @NotBlank(message = "Los objetivos trabajados son obligatorios")
        String objetivosTrabajados,
        String observaciones,
        String estrategiasYProximosPasos
) {
}