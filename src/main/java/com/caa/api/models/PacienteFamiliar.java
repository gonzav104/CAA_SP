package com.caa.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pacientes_familiares")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacienteFamiliar {

    @EmbeddedId
    private PacienteFamiliarId id;

    @MapsId("pacienteId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "paciente_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_pf_paciente")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Paciente paciente;

    @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "usuario_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_pf_usuario")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "permiso", nullable = false, columnDefinition = "permiso_colaborador")
    private PermisoColaborador permiso;

    @CreationTimestamp
    @Column(name = "vinculado_en", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime vinculadoEn;
}
