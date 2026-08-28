package com.caa.api.repositories;

import com.caa.api.models.Paciente;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PacienteRepository extends JpaRepository<Paciente, UUID> {
    List<Paciente> findByTerapeuta_Id(UUID terapeutaId);
}
