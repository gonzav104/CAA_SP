package com.caa.api.services.impl;

import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.ColaboradorService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ColaboradorServiceImpl implements ColaboradorService {

    private final PacienteFamiliarRepository pacienteFamiliarRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public ColaboradorResponseDTO vincularColaborador(UUID pacienteId, ColaboradorRegistroDTO dto, String emailTerapeuta) {
        Usuario terapeuta = terapeutaAutenticado(emailTerapeuta);
        Paciente paciente = pacienteDelTerapeuta(pacienteId, terapeuta.getId());

        Usuario familiar = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con el email " + dto.email()));

        if (familiar.getRol() != RolUsuario.FAMILIAR) {
            throw new IllegalArgumentException(
                    "Solo se pueden vincular usuarios con rol FAMILIAR");
        }

        if (pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiar.getId()).isPresent()) {
            throw new ConflictoException(
                    "El usuario ya es colaborador de este paciente");
        }

        PacienteFamiliarId id = new PacienteFamiliarId(paciente.getId(), familiar.getId());
        PacienteFamiliar vinculo = PacienteFamiliar.builder()
                .id(id)
                .paciente(paciente)
                .usuario(familiar)
                .permiso(dto.permiso())
                .build();

        PacienteFamiliar guardado = pacienteFamiliarRepository.saveAndFlush(vinculo);
        return toResponseDTO(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColaboradorResponseDTO> obtenerColaboradores(UUID pacienteId, String emailTerapeuta) {
        Usuario terapeuta = terapeutaAutenticado(emailTerapeuta);
        pacienteDelTerapeuta(pacienteId, terapeuta.getId());

        return pacienteFamiliarRepository.findByPaciente_Id(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public ColaboradorResponseDTO actualizarPermiso(UUID pacienteId, UUID usuarioId,
                                                    ColaboradorActualizacionDTO dto, String emailTerapeuta) {
        Usuario terapeuta = terapeutaAutenticado(emailTerapeuta);
        pacienteDelTerapeuta(pacienteId, terapeuta.getId());

        PacienteFamiliar vinculo = pacienteFamiliarRepository
                .findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario no es colaborador de este paciente"));

        vinculo.setPermiso(dto.permiso());
        PacienteFamiliar actualizado = pacienteFamiliarRepository.save(vinculo);
        return toResponseDTO(actualizado);
    }

    @Override
    @Transactional
    public void revocarColaborador(UUID pacienteId, UUID usuarioId, String emailTerapeuta) {
        Usuario terapeuta = terapeutaAutenticado(emailTerapeuta);
        pacienteDelTerapeuta(pacienteId, terapeuta.getId());

        PacienteFamiliar vinculo = pacienteFamiliarRepository
                .findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario no es colaborador de este paciente"));

        pacienteFamiliarRepository.delete(vinculo);
    }

    // ──────────────────────────────────────────────
    //  Helpers de ownership
    // ──────────────────────────────────────────────

    private Usuario terapeutaAutenticado(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));
    }

    private Paciente pacienteDelTerapeuta(UUID pacienteId, UUID terapeutaId) {
        return pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Paciente no encontrado o no tiene permisos"));
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
