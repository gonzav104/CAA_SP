package com.caa.api.repositories;

import com.caa.api.models.Organizacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizacionRepository extends JpaRepository<Organizacion, UUID> {

    /**
     * Bloqueo exclusivo de fila sobre la organización (design §3.3 punto 3): toda operación que
     * muta membresías (cambio de rolGestion, toggle esTerapeuta, baja, salida, transferencia,
     * aceptación de invitación de organización, asignación de terapeuta) lo adquiere PRIMERO,
     * antes de leer o escribir cualquier Membresia, para serializar esas mutaciones por organización.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organizacion o WHERE o.id = :id")
    Optional<Organizacion> findConBloqueoById(UUID id);
}
