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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Membresía de un {@link Usuario} en una {@link Organizacion} (design §2.3, §0).
 * <p>
 * {@code rolGestion} (gobernanza: OWNER/ADMIN/MIEMBRO) y {@code esTerapeuta} (capacidad
 * clínica, booleano) son atributos INDEPENDIENTES. Las cuatro combinaciones son estados
 * válidos sin restricción alguna (p. ej. ADMIN + esTerapeuta=false es un administrador puro;
 * MIEMBRO + esTerapeuta=true es un terapeuta sin rol de gobernanza). Ningún código de esta
 * entidad ni de ninguna otra parte de este cambio debe tratarlos como mutuamente excluyentes
 * ni introducir un valor de rol clínico dentro de {@link RolGestion}.
 */
@Entity
@Table(name = "membresias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membresia {

    @EmbeddedId
    private MembresiaId id;

    @MapsId("organizacionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organizacion_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membresia_organizacion")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organizacion organizacion;

    @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "usuario_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membresia_usuario")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rol_gestion", nullable = false)
    private RolGestion rolGestion;

    /** Lombok genera {@code isEsTerapeuta()} (no {@code getEsTerapeuta()}) para este booleano primitivo. */
    @Column(name = "es_terapeuta", nullable = false)
    private boolean esTerapeuta;

    @CreationTimestamp
    @Column(name = "unido_en", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime unidoEn;

    /**
     * Acceso de gestión (OWNER o ADMIN), independiente de {@code esTerapeuta}. Lee únicamente
     * los campos propios de esta instancia; cualquier verificación que dependa de OTRAS filas
     * (p. ej. asignaciones {@code PacienteTerapeuta}) vive en {@code AccesoService}, no aquí.
     */
    public boolean esGestion() {
        return rolGestion == RolGestion.OWNER || rolGestion == RolGestion.ADMIN;
    }
}
