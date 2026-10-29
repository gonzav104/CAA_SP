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
     * <p>
     * Los estados se enlazan con SpEL ({@code :#{T(...).X}}) y NO como literal de enum en el HQL: Hibernate
     * emite el literal de enum de un UPDATE como {@code cast(... as estadoinvitacion)}, un tipo que no existe
     * en PostgreSQL (el enum real es {@code estado_invitacion}); H2 sí lo crea con ese nombre y lo ocultaba.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Invitacion i SET i.estado = :#{T(com.caa.api.models.EstadoInvitacion).ACEPTADA}, "
            + "i.aceptadaPor = :usuario, i.resueltaEn = :ahora "
            + "WHERE i.id = :id AND i.estado = :#{T(com.caa.api.models.EstadoInvitacion).PENDIENTE} "
            + "AND i.expiraEn > :ahora")
    int aceptarSiVigente(@Param("id") UUID id, @Param("usuario") Usuario usuario, @Param("ahora") LocalDateTime ahora);

    /**
     * Revocación condicional: solo transiciona PENDIENTE -&gt; REVOCADA. Igual que
     * {@link #aceptarSiVigente}, evita la carrera de leer-y-guardar contra un accept concurrente
     * (0 filas si ya está resuelta) y no toca {@code aceptadaPor}.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Invitacion i SET i.estado = :#{T(com.caa.api.models.EstadoInvitacion).REVOCADA}, "
            + "i.resueltaEn = :ahora WHERE i.id = :id "
            + "AND i.estado = :#{T(com.caa.api.models.EstadoInvitacion).PENDIENTE}")
    int revocarSiPendiente(@Param("id") UUID id, @Param("ahora") LocalDateTime ahora);
}
