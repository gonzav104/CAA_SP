package com.caa.api.repositories;

import com.caa.api.models.Paciente;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    /** Workspace completo: {@code GET /api/organizaciones/{id}/pacientes} para rolGestion OWNER/ADMIN. */
    List<Paciente> findByOrganizacion_Id(UUID organizacionId);

    /**
     * Pacientes de la organización asignados clínicamente al usuario (design-part2 §14 item 3):
     * {@code GET /api/organizaciones/{id}/pacientes} para MIEMBRO con esTerapeuta=true.
     */
    @Query("SELECT p FROM Paciente p WHERE p.organizacion.id = :organizacionId "
            + "AND EXISTS (SELECT 1 FROM PacienteTerapeuta pt WHERE pt.paciente = p AND pt.usuario.id = :usuarioId)")
    List<Paciente> findAsignadosEnOrganizacion(@Param("organizacionId") UUID organizacionId,
                                                @Param("usuarioId") UUID usuarioId);

    /**
     * Unión de {@code GET /api/pacientes} en una sola consulta (design-part2 §14 item 3):
     * todo paciente de una organización de gestión (OWNER/ADMIN, acceso organization-wide) O
     * asignado clínicamente dentro de una organización donde el usuario es MIEMBRO con
     * esTerapeuta=true. El llamante omite esta consulta por completo cuando ambas colecciones
     * están vacías (ningún resultado posible); cuando solo una está vacía, esa rama del OR
     * simplemente no empareja ninguna fila.
     */
    @Query("SELECT p FROM Paciente p WHERE p.organizacion.id IN :orgIdsGestion "
            + "OR (p.organizacion.id IN :orgIdsClinicas "
            + "AND EXISTS (SELECT 1 FROM PacienteTerapeuta pt WHERE pt.paciente = p AND pt.usuario.id = :usuarioId))")
    List<Paciente> findAccesiblesPorEquipo(@Param("orgIdsGestion") Collection<UUID> orgIdsGestion,
                                            @Param("orgIdsClinicas") Collection<UUID> orgIdsClinicas,
                                            @Param("usuarioId") UUID usuarioId);

    /** {@code DELETE /api/organizaciones/{id}} (fase 4): bloquea el borrado mientras haya pacientes. */
    boolean existsByOrganizacion_Id(UUID organizacionId);
}
