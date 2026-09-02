package com.caa.api.services.impl;

import com.caa.api.dtos.PacienteActualizacionDTO;
import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.PacienteService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PacienteServiceImpl implements PacienteService {

    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PacienteFamiliarRepository pacienteFamiliarRepository;

    @Override
    public PacienteResponseDTO registrarPaciente(PacienteRegistroDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        Paciente paciente = Paciente.builder()
                .nombre(dto.nombre())
                .apellido(dto.apellido())
                .fechaNacimiento(dto.fechaNacimiento())
                .terapeuta(terapeuta)
                .build();

        Paciente pacienteGuardado = pacienteRepository.save(paciente);

        return new PacienteResponseDTO(
                pacienteGuardado.getId(),
                pacienteGuardado.getNombre(),
                pacienteGuardado.getApellido(),
                pacienteGuardado.getFechaNacimiento(),
                pacienteGuardado.getCreadoEn()
        );
    }

    @Override
    public List<PacienteResponseDTO> obtenerMisPacientes(String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        return pacienteRepository.findByTerapeutaId(terapeuta.getId()).stream()
                .map(p -> new PacienteResponseDTO(
                        p.getId(),
                        p.getNombre(),
                        p.getApellido(),
                        p.getFechaNacimiento(),
                        p.getCreadoEn()
                ))
                .toList();
    }

    @Override
    public PacienteResponseDTO obtenerPaciente(UUID id, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        // Maneja terapeuta propietario y familiar asignado (vía pacienteLegibleParaUsuario)
        Paciente paciente = pacienteLegibleParaUsuario(id, usuario);

        return new PacienteResponseDTO(
                paciente.getId(),
                paciente.getNombre(),
                paciente.getApellido(),
                paciente.getFechaNacimiento(),
                paciente.getCreadoEn()
        );
    }

    @Override
    public PacienteResponseDTO actualizarPaciente(UUID id, PacienteActualizacionDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(id, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        paciente.setNombre(dto.nombre());
        paciente.setApellido(dto.apellido());
        paciente.setFechaNacimiento(dto.fechaNacimiento());

        Paciente pacienteActualizado = pacienteRepository.save(paciente);

        return new PacienteResponseDTO(
                pacienteActualizado.getId(),
                pacienteActualizado.getNombre(),
                pacienteActualizado.getApellido(),
                pacienteActualizado.getFechaNacimiento(),
                pacienteActualizado.getCreadoEn()
        );
    }

    @Override
    public void eliminarPaciente(UUID id, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(id, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        pacienteRepository.delete(paciente);
    }

    @Override
    public Paciente pacienteLegibleParaUsuario(UUID pacienteId, Usuario usuario) {
        if (usuario.getRol() == RolUsuario.TERAPEUTA) {
            return pacienteDelTerapeuta(pacienteId, usuario.getId());
        } else if (usuario.getRol() == RolUsuario.FAMILIAR) {
            return pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuario.getId())
                    .map(PacienteFamiliar::getPaciente)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No tiene acceso a este paciente"));
        }
        throw new RecursoNoEncontradoException("Rol desconocido");
    }

    @Override
    public void verificarEdicionParaUsuario(UUID pacienteId, Usuario usuario) {
        if (usuario.getRol() == RolUsuario.TERAPEUTA) {
            pacienteDelTerapeuta(pacienteId, usuario.getId());
        } else if (usuario.getRol() == RolUsuario.FAMILIAR) {
            PermisoColaborador permiso = pacienteFamiliarRepository
                    .findByPaciente_IdAndUsuario_Id(pacienteId, usuario.getId())
                    .map(PacienteFamiliar::getPermiso)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No tiene acceso a este paciente"));
            if (permiso != PermisoColaborador.EDICION_LIMITADA) {
                throw new RecursoNoEncontradoException(
                        "No tiene permisos de edición sobre este paciente");
            }
        } else {
            throw new RecursoNoEncontradoException("Rol desconocido");
        }
    }

    private Paciente pacienteDelTerapeuta(UUID pacienteId, UUID terapeutaId) {
        return pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));
    }
}
