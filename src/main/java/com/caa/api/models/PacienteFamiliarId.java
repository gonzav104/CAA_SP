package com.caa.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PacienteFamiliarId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;
}
