package com.caa.api.services.impl;

import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.ColaboradorService;
import com.caa.api.services.InvitacionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de colaboradores (familiares) de un paciente (spec {@code patient-collaborators}
 * MODIFICADA; design-part2 §11.2, §13). Autorización vía {@code AccesoService.Capacidad.
 * GESTION_CLINICA} (rolGestion OWNER/ADMIN de la organización del paciente, o miembro asignado
 * con esTerapeuta=true) — ya NO vía {@code terapeuta_id} directo ni filtro global
 * {@code RolUsuario.FAMILIAR}. Agregar un colaborador ahora crea una invitación
 * {@code PACIENTE_FAMILIAR}: el email destino no necesita tener cuenta todavía, y nada se
 * concede hasta que la invitación se acepta (ver {@link InvitacionService}).
 */
@Service
@RequiredArgsConstructor
public class ColaboradorServiceImpl implements ColaboradorService {

    private final PacienteFamiliarRepository pacienteFamiliarRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoService accesoService;
    private final InvitacionService invitacionService;

    @Override
    @Transactional
    public InvitacionResponseDTO vincularColaborador(UUID pacienteId, ColaboradorRegistroDTO dto,
                                                       String emailTerapeuta) {
        // La autorización (Capacidad.GESTION_CLINICA sobre el paciente) y la no-enumeración de
        // cuentas ya existentes/pendientes viven en InvitacionServiceImpl.crearFamiliar.
        return invitacionService.crearFamiliar(pacienteId, dto.email(), dto.permiso(), emailTerapeuta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColaboradorResponseDTO> obtenerColaboradores(UUID pacienteId, String emailTerapeuta) {
        Usuario usuario = usuarioAutenticado(emailTerapeuta);
        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        return pacienteFamiliarRepository.findByPaciente_Id(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public ColaboradorResponseDTO actualizarPermiso(UUID pacienteId, UUID usuarioId,
                                                    ColaboradorActualizacionDTO dto, String emailTerapeuta) {
        Usuario usuario = usuarioAutenticado(emailTerapeuta);
        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        PacienteFamiliar vinculo = pacienteFamiliarRepository
                .findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        vinculo.setPermiso(dto.permiso());
        PacienteFamiliar actualizado = pacienteFamiliarRepository.save(vinculo);
        return toResponseDTO(actualizado);
    }

    @Override
    @Transactional
    public void revocarColaborador(UUID pacienteId, UUID usuarioId, String emailTerapeuta) {
        Usuario usuario = usuarioAutenticado(emailTerapeuta);
        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        PacienteFamiliar vinculo = pacienteFamiliarRepository
                .findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteFamiliarRepository.delete(vinculo);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Usuario usuarioAutenticado(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private ColaboradorResponseDTO toResponseDTO(PacienteFamiliar pf) {
        Usuario usuario = pf.getUsuario();
        return new ColaboradorResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                pf.getPermiso(),
                pf.getVinculadoEn()
        );
    }
}
