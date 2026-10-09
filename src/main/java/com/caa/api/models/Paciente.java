package com.caa.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "pacientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * LEGACY: relajado a opcional/nullable durante el período de dual-write (design §2.6, §7).
     * Se sigue escribiendo (siempre el usuario creador) mientras la columna de base de datos es
     * NOT NULL, pero deja de leerse para autorización en este cambio: el acceso organizacional
     * pasa por {@code organizacion} + {@code Membresia} + {@code PacienteTerapeuta}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "terapeuta_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_paciente_terapeuta")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario terapeuta;

    /**
     * Organización a la que pertenece el paciente (design §2.6). Nullable en JPA durante el
     * período de dual-write (MIGRACIÓN 014-015); se endurece a {@code optional=false,
     * nullable=false} en el cutover (MIGRACIÓN 016, fuera de alcance de esta fase).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "organizacion_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_paciente_organizacion")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organizacion organizacion;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "grid_size")
    private Integer gridSize;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime creadoEn;
}
