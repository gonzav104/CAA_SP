package com.caa.api.services.impl;

import com.caa.api.dtos.PacienteActualizacionDTO;
import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.models.Paciente;
import com.caa.api.models.Usuario;
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

    @Override
    public PacienteResponseDTO registrarPaciente(PacienteRegistroDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

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
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

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
    public PacienteResponseDTO actualizarPaciente(UUID id, PacienteActualizacionDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(id, terapeuta.getId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado o no tiene permisos"));

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
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(id, terapeuta.getId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado o no tiene permisos"));

        pacienteRepository.delete(paciente);
    }
}
