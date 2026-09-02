package com.caa.api.services.impl;

import com.caa.api.dtos.SesionRegistroDTO;
import com.caa.api.dtos.SesionResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.Sesion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.SesionRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.PacienteService;
import com.caa.api.services.SesionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SesionServiceImpl implements SesionService {

    private final SesionRepository sesionRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PacienteService pacienteService;

    @Override
    public SesionResponseDTO registrarSesion(UUID pacienteId, SesionRegistroDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        Sesion sesion = Sesion.builder()
                .paciente(paciente)
                .fechaHora(dto.fechaHora())
                .disposicion(dto.disposicion())
                .objetivosTrabajados(dto.objetivosTrabajados())
                .observaciones(dto.observaciones())
                .estrategiasYProximosPasos(dto.estrategiasYProximosPasos())
                .build();

        Sesion sesionGuardada = sesionRepository.save(sesion);

        return toResponseDTO(sesionGuardada);
    }

    @Override
    public List<SesionResponseDTO> obtenerSesionesDePaciente(UUID pacienteId, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Permite leer al terapeuta propietario Y al familiar asignado (solo lectura).
        // Cualquiera sin acceso → 404 (el helper lanza la excepción del dominio).
        pacienteService.pacienteLegibleParaUsuario(pacienteId, usuario);

        return sesionRepository.findByPacienteId(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    private SesionResponseDTO toResponseDTO(Sesion sesion) {
        return new SesionResponseDTO(
                sesion.getId(),
                sesion.getFechaHora(),
                sesion.getDisposicion(),
                sesion.getObjetivosTrabajados(),
                sesion.getObservaciones(),
                sesion.getEstrategiasYProximosPasos(),
                sesion.getCreadoEn(),
                sesion.getPaciente().getId()
        );
    }
}
