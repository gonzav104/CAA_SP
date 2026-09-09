package com.caa.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "pictogramas_globales")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PictogramaGlobal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "etiqueta", nullable = false, length = 100)
    private String etiqueta;

    @Column(name = "imagen_url", nullable = false, columnDefinition = "TEXT")
    private String imagenUrl;

    @Column(name = "arasaac_id")
    private Long arasaacId;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime creadoEn;
}
