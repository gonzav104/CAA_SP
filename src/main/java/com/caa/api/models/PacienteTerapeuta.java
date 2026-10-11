package com.caa.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Asignación clínica many-to-many entre {@link Paciente} y {@link Usuario} (design §2.4).
 * <p>
 * Totalmente separada de {@link PacienteFamiliar} (decisión 6 del diseño). Un paciente puede
 * tener cero filas (sin asignar — estado válido) o varias (varios terapeutas simultáneos).
 * Invariante aplicacional (design §3.5): solo existe una fila para un miembro cuya
 * {@code Membresia.esTerapeuta = true} en la organización del paciente; no se expresa como
 * CHECK de base de datos porque agregaría una columna/índice redundante con la regla
 * transaccional ya aplicada por la capa de servicio (CLAUDE.md §13: evitar sobre-ingeniería).
 */
@Entity
@Table(name = "pacientes_terapeutas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacienteTerapeuta {

    @EmbeddedId
    private PacienteTerapeutaId id;

    @MapsId("pacienteId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "paciente_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_pt_paciente")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Paciente paciente;

    @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "usuario_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_pt_usuario")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario usuario;

    /**
     * Denormalizado a propósito (design §2.4): habilita los FK compuestos
     * {@code fk_pt_membresia (organizacion_id, usuario_id) -> membresias} y
     * {@code fk_pt_paciente_organizacion (paciente_id, organizacion_id) -> pacientes(id, organizacion_id)},
     * que garantizan a nivel de base de datos que el asignado es miembro de la MISMA
     * organización del paciente. Lo fija la capa de servicio desde
     * {@code paciente.getOrganizacion().getId()}; nunca se deriva vía relación JPA.
     */
    @Column(name = "organizacion_id", nullable = false)
    private UUID organizacionId;

    @CreationTimestamp
    @Column(name = "asignado_en", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime asignadoEn;
}
