package com.caa.api.dtos;

import com.caa.api.models.RolGestion;
import java.util.UUID;

/**
 * design §13: {@code OrganizacionResponseDTO{id, nombre, miRolGestion, miEsTerapeuta}}. Usado por
 * creación, listado de "mis organizaciones" y obtención por id.
 */
public record OrganizacionResponseDTO(
        UUID id,
        String nombre,
        RolGestion miRolGestion,
        boolean miEsTerapeuta
) {
}
