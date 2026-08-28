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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "categorias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "cartilla_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_categoria_cartilla")
    )
    private Cartilla cartilla;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "orden", nullable = false)
    private int orden;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime creadoEn;
}
