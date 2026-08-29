package com.caa.api.repositories;

import com.caa.api.models.Sesion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SesionRepository extends JpaRepository<Sesion, UUID> {
    List<Sesion> findByPacienteId(UUID pacienteId);
}
