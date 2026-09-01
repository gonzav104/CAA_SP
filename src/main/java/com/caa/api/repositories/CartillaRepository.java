package com.caa.api.repositories;

import com.caa.api.models.Cartilla;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartillaRepository extends JpaRepository<Cartilla, UUID> {
    List<Cartilla> findByPacienteId(UUID pacienteId);

    Optional<Cartilla> findByIdAndPacienteId(UUID id, UUID pacienteId);

    Optional<Cartilla> findByPaciente_IdAndEsPrincipalTrue(UUID pacienteId);
}
