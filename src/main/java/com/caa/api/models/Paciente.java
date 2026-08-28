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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "terapeuta_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_paciente_terapeuta")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario terapeuta;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime creadoEn;
}
