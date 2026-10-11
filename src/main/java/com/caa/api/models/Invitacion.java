package com.caa.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Invitación persistida (design §2.5, §9). Exactamente uno de {@code organizacion}/{@code paciente}
 * está presente según {@code tipo} (CHECK {@code chk_invitacion_contexto} en init.sql, MIGRACIÓN 014).
 * {@code rolGestionPropuesto} y {@code esTerapeutaPropuesto} son independientes entre sí (igual que
 * en {@link Membresia}); {@code rolGestionPropuesto} nunca puede ser {@code OWNER} (aplicado en la
 * capa de servicio, no en esta entidad).
 */
@Entity
@Table(name = "invitaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false)
    private TipoInvitacion tipo;

    /** Solo presente cuando {@code tipo = ORGANIZACION}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organizacion_id",
            foreignKey = @ForeignKey(name = "fk_invitacion_organizacion")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organizacion organizacion;

    /** Solo presente cuando {@code tipo = PACIENTE_FAMILIAR}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "paciente_id",
            foreignKey = @ForeignKey(name = "fk_invitacion_paciente")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Paciente paciente;

    /** Normalizado: {@code trim().toLowerCase(Locale.ROOT)}, aplicado por la capa de servicio. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Solo para {@code tipo = ORGANIZACION}; nunca {@code OWNER} (validado en servicio + CHECK). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rol_gestion_propuesto")
    private RolGestion rolGestionPropuesto;

    /**
     * Solo para {@code tipo = ORGANIZACION}; wrapper {@code Boolean} (no {@code boolean}) porque
     * la columna es NULL para invitaciones de tipo PACIENTE_FAMILIAR.
     */
    @Column(name = "es_terapeuta_propuesto")
    private Boolean esTerapeutaPropuesto;

    /** Solo para {@code tipo = PACIENTE_FAMILIAR}. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "permiso_propuesto")
    private PermisoColaborador permisoPropuesto;

    /** SHA-256 hex del token crudo (idéntico esquema a {@code Usuario.resetToken}); el token crudo nunca se persiste. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoInvitacion estado;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    /** Metadato de auditoría. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "invitado_por_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_invitacion_invitado_por")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario invitadoPor;

    /** Metadato de auditoría; null hasta que la invitación se acepta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "aceptada_por_id",
            foreignKey = @ForeignKey(name = "fk_invitacion_aceptada_por")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario aceptadaPor;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime creadoEn;

    /** Se fija al transicionar a ACEPTADA, REVOCADA o EXPIRADA. */
    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;
}
