package com.caa.api.repositories;

import com.caa.api.models.PacienteTerapeuta;
import com.caa.api.models.PacienteTerapeutaId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PacienteTerapeutaRepository extends JpaRepository<PacienteTerapeuta, PacienteTerapeutaId> {

    @EntityGraph(attributePaths = "usuario")
    List<PacienteTerapeuta> findByPaciente_Id(UUID pacienteId);

    /**
     * Usado tanto por la baja de un miembro como por el toggle esTerapeuta=true->false
     * (design §3.5): borra en bloque, nunca fila por fila, las asignaciones de ese usuario
     * EN ESA organización (el usuario puede tener asignaciones en otra organización, que no
     * deben verse afectadas).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PacienteTerapeuta pt WHERE pt.organizacionId = :organizacionId AND pt.usuario.id = :usuarioId")
    int deleteByOrganizacionIdAndUsuario_Id(UUID organizacionId, UUID usuarioId);
}
