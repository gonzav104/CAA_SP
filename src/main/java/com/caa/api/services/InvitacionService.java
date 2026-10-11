package com.caa.api.services;

import com.caa.api.dtos.AceptarInvitacionDTO;
import com.caa.api.dtos.AceptarInvitacionResponseDTO;
import com.caa.api.dtos.InvitacionOrganizacionRegistroDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.models.PermisoColaborador;
import java.util.List;
import java.util.UUID;

/**
 * Invitaciones a una organización o a un paciente (familiar) (design §9, spec {@code invitations}).
 * Nada se concede hasta la aceptación: crear una {@code Invitacion} nunca crea una
 * {@code Membresia} ni un {@code PacienteFamiliar}.
 */
public interface InvitacionService {

    /** OWNER puede proponer ADMIN/MIEMBRO; ADMIN solo MIEMBRO. {@code esTerapeuta} es libre para ambos. */
    InvitacionResponseDTO crearOrganizacion(UUID organizacionId, InvitacionOrganizacionRegistroDTO dto,
                                             String emailInvitador);

    /** Solo invitaciones PENDIENTE; restringido a rolGestion OWNER/ADMIN. */
    List<InvitacionResponseDTO> listarOrganizacion(UUID organizacionId, String emailInvitador);

    /** OWNER/ADMIN. Bloquea cualquier aceptación posterior con el token original. */
    void revocarOrganizacion(UUID organizacionId, UUID invitacionId, String emailInvitador);

    /** Requiere Capacidad.GESTION_CLINICA sobre el paciente. No exige que el email ya tenga cuenta. */
    InvitacionResponseDTO crearFamiliar(UUID pacienteId, String emailInvitado, PermisoColaborador permiso,
                                         String emailInvitador);

    /** Solo invitaciones PENDIENTE; restringido a Capacidad.GESTION_CLINICA sobre el paciente. */
    List<InvitacionResponseDTO> listarFamiliar(UUID pacienteId, String emailInvitador);

    /** Capacidad.GESTION_CLINICA sobre el paciente. */
    void revocarFamiliar(UUID pacienteId, UUID invitacionId, String emailInvitador);

    /**
     * Acepta una invitación de cualquier tipo (design §9.4): token inexistente, no PENDIENTE,
     * expirado, o email no coincidente -&gt; el mismo error genérico (sin distinguir el caso).
     */
    AceptarInvitacionResponseDTO aceptar(AceptarInvitacionDTO dto, String emailAutenticado);
}
