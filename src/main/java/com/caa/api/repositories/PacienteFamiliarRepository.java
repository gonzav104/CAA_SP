package com.caa.api.repositories;

import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PacienteFamiliarRepository extends JpaRepository<PacienteFamiliar, PacienteFamiliarId> {
    List<PacienteFamiliar> findByUsuario_Id(UUID usuarioId);
}
