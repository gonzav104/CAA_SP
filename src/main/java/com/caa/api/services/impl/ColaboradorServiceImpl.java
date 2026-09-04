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

        // C1+C2: indistinguible entre "cuenta inexistente" y "rol ≠ FAMILIAR" (404 genérico)
        Usuario familiar = usuarioRepository.findByEmail(dto.email())
                .filter(u -> u.getRol() == RolUsuario.FAMILIAR)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        if (pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiar.getId()).isPresent()) {
            throw new ConflictoException(
                    "No se puede vincular este usuario");
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
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

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
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteFamiliarRepository.delete(vinculo);
    }

    // ──────────────────────────────────────────────
    //  Helpers de ownership
    // ──────────────────────────────────────────────

    private Usuario terapeutaAutenticado(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
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
