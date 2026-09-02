package com.caa.api.dtos;

public record GoogleAuthResponseDTO(
        boolean requiereRol,
        String tipo,
        String email,
        String nombre
) {
}