package com.caa.api.repositories;

import com.caa.api.models.Cartilla;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CartillaRepository extends JpaRepository<Cartilla, UUID> {
    List<Cartilla> findByPacienteId(UUID pacienteId);

    Optional<Cartilla> findByIdAndPacienteId(UUID id, UUID pacienteId);

    /**
     * Desmarca TODAS las cartillas principales del paciente (alta de una nueva principal).
     * Se ejecuta de inmediato (UPDATE masivo) y hace flush previo de lo pendiente.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Cartilla c SET c.esPrincipal = false "
            + "WHERE c.paciente.id = :pacienteId AND c.esPrincipal = true")
    int desmarcarPrincipalesDe(@Param("pacienteId") UUID pacienteId);

    /**
     * Desmarca las principales del paciente EXCEPTO la cartilla indicada (cambio de principal
     * sobre una cartilla existente). Se ejecuta de inmediato y hace flush previo de lo pendiente.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Cartilla c SET c.esPrincipal = false "
            + "WHERE c.paciente.id = :pacienteId AND c.id <> :cartillaId AND c.esPrincipal = true")
    int desmarcarOtrasPrincipalesDe(@Param("pacienteId") UUID pacienteId,
                                    @Param("cartillaId") UUID cartillaId);
}
