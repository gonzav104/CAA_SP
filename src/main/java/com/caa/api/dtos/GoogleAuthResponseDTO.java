package com.caa.api.dtos;

public record GoogleAuthResponseDTO(
        boolean requiereRegistro,
        String tipo,
        String email,
        String nombre
) {
}