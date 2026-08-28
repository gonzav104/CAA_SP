package com.caa.api.repositories;

import com.caa.api.models.PictogramaCustom;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PictogramaCustomRepository extends JpaRepository<PictogramaCustom, UUID> {
    List<PictogramaCustom> findByPaciente_Id(UUID pacienteId);
}
