package com.caa.api.services.impl;

import com.caa.api.dtos.SesionActualizacionDTO;
import com.caa.api.dtos.SesionRegistroDTO;
import com.caa.api.dtos.SesionResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Sesion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.SesionRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.SesionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SesionServiceImpl implements SesionService {

    private final SesionRepository sesionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoService accesoService;

    @Override
    public SesionResponseDTO registrarSesion(UUID pacienteId, SesionRegistroDTO dto, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Las sesiones son un recurso clínico (design-part2 §11.2): GESTION_CLINICA exige
        // acceso de equipo (gestión OWNER/ADMIN o miembro clínico asignado). Un acceso
        // puramente familiar nunca satisface esEquipo(), así que la exclusión de familiares
        // (regla funcional sin cambios) se preserva automáticamente.
        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        Sesion sesion = Sesion.builder()
                .paciente(acceso.paciente())
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
    @Transactional(readOnly = true)
    public List<SesionResponseDTO> obtenerSesionesDePaciente(UUID pacienteId, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        return sesionRepository.findByPacienteId(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SesionResponseDTO obtenerSesion(UUID pacienteId, UUID sesionId, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        Sesion sesion = sesionRepository.findByIdAndPacienteId(sesionId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sesión no encontrada o no tiene permisos"));

        return toResponseDTO(sesion);
    }

    @Override
    @Transactional
    public SesionResponseDTO actualizarSesion(UUID pacienteId, UUID sesionId, SesionActualizacionDTO dto, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        Sesion sesion = sesionRepository.findByIdAndPacienteId(sesionId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sesión no encontrada o no tiene permisos"));

        sesion.setFechaHora(dto.fechaHora());
        sesion.setDisposicion(dto.disposicion());
        sesion.setObjetivosTrabajados(dto.objetivosTrabajados());
        sesion.setObservaciones(dto.observaciones());
        sesion.setEstrategiasYProximosPasos(dto.estrategiasYProximosPasos());

        Sesion actualizada = sesionRepository.save(sesion);
        return toResponseDTO(actualizada);
    }

    @Override
    @Transactional
    public void eliminarSesion(UUID pacienteId, UUID sesionId, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        Sesion sesion = sesionRepository.findByIdAndPacienteId(sesionId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sesión no encontrada o no tiene permisos"));

        sesionRepository.delete(sesion);
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
