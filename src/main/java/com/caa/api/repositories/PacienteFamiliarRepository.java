package com.caa.api.repositories;

import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PacienteFamiliarRepository extends JpaRepository<PacienteFamiliar, PacienteFamiliarId> {
    // El nombre del método no cambia, por lo que Spring Data deriva el mismo predicado
    // WHERE usuario_id = ?; @EntityGraph solo amplía el plan de fetch tras derivar el predicado.
    @EntityGraph(attributePaths = "paciente")
    List<PacienteFamiliar> findByUsuario_Id(UUID usuarioId);

    List<PacienteFamiliar> findByPaciente_Id(UUID pacienteId);

    Optional<PacienteFamiliar> findByPaciente_IdAndUsuario_Id(UUID pacienteId, UUID usuarioId);
}
