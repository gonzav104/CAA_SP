package com.caa.api.repositories;

import com.caa.api.models.ItemCartilla;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemCartillaRepository extends JpaRepository<ItemCartilla, UUID> {
    List<ItemCartilla> findByCategoriaIdOrderByOrdenVisualAsc(UUID categoriaId);

    // Fetch plan widened via @EntityGraph: recursoGlobal/recursoCustom son @ManyToOne(optional = true)
    // por lo que Hibernate emite LEFT OUTER JOIN sobre ellos; categoria es optional = false
    // (categoria_id NOT NULL), así que su join no puede sumar ni descartar filas en ningún caso.
    @EntityGraph(attributePaths = {"categoria", "recursoGlobal", "recursoCustom"})
    List<ItemCartilla> findByCategoriaIdInOrderByOrdenVisualAsc(Collection<UUID> categoriaIds);

    Optional<ItemCartilla> findByIdAndCategoriaId(UUID id, UUID categoriaId);

    boolean existsByRecursoCustomId(UUID recursoCustomId);
}
