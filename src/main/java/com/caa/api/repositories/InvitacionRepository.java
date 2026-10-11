package com.caa.api.repositories;

import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.Invitacion;
import com.caa.api.models.Usuario;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface InvitacionRepository extends JpaRepository<Invitacion, UUID> {

    Optional<Invitacion> findByTokenHash(String tokenHash);

    List<Invitacion> findByOrganizacion_IdAndEstado(UUID organizacionId, EstadoInvitacion estado);

    List<Invitacion> findByPaciente_IdAndEstado(UUID pacienteId, EstadoInvitacion estado);

    Optional<Invitacion> findByOrganizacion_IdAndEmailAndEstado(UUID organizacionId, String email, EstadoInvitacion estado);

    Optional<Invitacion> findByPaciente_IdAndEmailAndEstado(UUID pacienteId, String email, EstadoInvitacion estado);

    /**
     * Update condicional de un solo uso (design §9.2): solo transiciona PENDIENTE -> ACEPTADA
     * si no expiró. Devuelve la cantidad de filas afectadas (0 o 1); el servicio solo continúa
     * si devuelve 1 — nunca confía en una lectura previa seguida de un update separado, que
     * dejaría una ventana de carrera para un accept doble.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Invitacion i SET i.estado = com.caa.api.models.EstadoInvitacion.ACEPTADA, "
            + "i.aceptadaPor = :usuario, i.resueltaEn = :ahora "
            + "WHERE i.id = :id AND i.estado = com.caa.api.models.EstadoInvitacion.PENDIENTE AND i.expiraEn > :ahora")
    int aceptarSiVigente(@Param("id") UUID id, @Param("usuario") Usuario usuario, @Param("ahora") LocalDateTime ahora);
}
