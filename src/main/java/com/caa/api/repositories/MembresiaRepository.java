package com.caa.api.repositories;

import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembresiaRepository extends JpaRepository<Membresia, MembresiaId> {

    // El nombre del método no cambia; @EntityGraph solo amplía el plan de fetch tras derivar el
    // predicado WHERE usuario_id = ? (mismo idiom que PacienteFamiliarRepository.findByUsuario_Id).
    @EntityGraph(attributePaths = "organizacion")
    List<Membresia> findByUsuario_Id(UUID usuarioId);

    @EntityGraph(attributePaths = "usuario")
    List<Membresia> findByOrganizacion_Id(UUID organizacionId);

    boolean existsByUsuario_Id(UUID usuarioId);
}
