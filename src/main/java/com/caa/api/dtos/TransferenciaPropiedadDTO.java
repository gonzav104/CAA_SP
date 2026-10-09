package com.caa.api.dtos;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /api/organizaciones/{organizacionId}/transferencia-propiedad} (design §12.2, design
 * part 1 "Ownership Transfer Is Atomic, Concurrency-Safe, and Leaves esTerapeuta Untouched").
 */
public record TransferenciaPropiedadDTO(
        @NotNull(message = "El usuario que recibirá la propiedad es obligatorio") UUID nuevoOwnerUsuarioId
) {
}
