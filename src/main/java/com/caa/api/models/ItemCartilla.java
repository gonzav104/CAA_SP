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
@Table(name = "items_cartilla")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemCartilla {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "categoria_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_categoria")
    )
    private Categoria categoria;

    @Column(name = "texto_hablado", nullable = false, length = 255)
    private String textoHablado;

    @Column(name = "orden_visual", nullable = false)
    private int ordenVisual;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "recurso_global_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_item_global")
    )
    private PictogramaGlobal recursoGlobal;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "recurso_custom_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_item_custom")
    )
    private PictogramaCustom recursoCustom;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime creadoEn;
}
