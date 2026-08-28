package com.caa.api.dtos;

public record AuthResponseDTO(String token, String tipo) {

    public AuthResponseDTO {
        if (tipo == null || tipo.isBlank()) {
            tipo = "Bearer";
        }
    }
}
