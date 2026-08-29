package com.caa.api.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record SesionResponseDTO(
        UUID id,
        LocalDateTime fechaHora,
        String disposicion,
        String objetivosTrabajados,
        String observaciones,
        String estrategiasYProximosPasos,
        LocalDateTime creadoEn,
        UUID pacienteId
) {
}
