package com.caa.api.services.impl;

import com.caa.api.dtos.PictogramaCustomActualizacionDTO;
import com.caa.api.dtos.PictogramaCustomRegistroDTO;
import com.caa.api.dtos.PictogramaCustomResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.CloudinaryService;
import com.caa.api.services.PacienteService;
import com.caa.api.services.PictogramaCustomService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PictogramaCustomServiceImpl implements PictogramaCustomService {

    private final PictogramaCustomRepository pictogramaCustomRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ItemCartillaRepository itemCartillaRepository;
    private final PacienteService pacienteService;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    public List<PictogramaCustomResponseDTO> obtenerPictogramasDePaciente(UUID pacienteId, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteService.pacienteLegibleParaUsuario(pacienteId, usuario);

        return pictogramaCustomRepository.findByPaciente_Id(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PictogramaCustomResponseDTO obtenerPictograma(UUID pacienteId, UUID id, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteService.pacienteLegibleParaUsuario(pacienteId, usuario);

        PictogramaCustom pictograma = pictogramaCustomRepository.findById(id)
                .filter(p -> p.getPaciente().getId().equals(pacienteId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Pictograma custom no encontrado o no tiene permisos"));

        return toResponseDTO(pictograma);
    }

    @Override
    @Transactional
    public PictogramaCustomResponseDTO crearPictograma(UUID pacienteId, PictogramaCustomRegistroDTO dto,
                                                       MultipartFile archivo, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteService.verificarEdicionParaUsuario(pacienteId, usuario);

        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        String imagenUrl = cloudinaryService.subirImagen(archivo, pacienteId);

        PictogramaCustom pictograma = PictogramaCustom.builder()
                .paciente(paciente)
                .etiqueta(dto.etiqueta())
                .imagenUrl(imagenUrl)
                .build();

        PictogramaCustom guardado = pictogramaCustomRepository.save(pictograma);
        return toResponseDTO(guardado);
    }

    @Override
    @Transactional
    public PictogramaCustomResponseDTO actualizarPictograma(UUID pacienteId, UUID id, PictogramaCustomActualizacionDTO dto,
                                                            MultipartFile archivo, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteService.verificarEdicionParaUsuario(pacienteId, usuario);

        PictogramaCustom pictograma = pictogramaCustomRepository.findById(id)
                .filter(p -> p.getPaciente().getId().equals(pacienteId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Pictograma custom no encontrado o no tiene permisos"));

        if (dto != null && dto.etiqueta() != null && !dto.etiqueta().isBlank()) {
            pictograma.setEtiqueta(dto.etiqueta());
        }

        if (archivo != null && !archivo.isEmpty()) {
            String imagenUrl = cloudinaryService.subirImagen(archivo, pacienteId);
            pictograma.setImagenUrl(imagenUrl);
        }

        PictogramaCustom actualizado = pictogramaCustomRepository.save(pictograma);
        return toResponseDTO(actualizado);
    }

    @Override
    @Transactional
    public void eliminarPictograma(UUID pacienteId, UUID id, String emailUsuario) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        PictogramaCustom pictograma = pictogramaCustomRepository.findById(id)
                .filter(p -> p.getPaciente().getId().equals(pacienteId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Pictograma custom no encontrado o no tiene permisos"));

        if (itemCartillaRepository.existsByRecursoCustomId(id)) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el pictograma porque está en uso en al menos un ítem");
        }

        pictogramaCustomRepository.delete(pictograma);
    }

    private PictogramaCustomResponseDTO toResponseDTO(PictogramaCustom p) {
        return new PictogramaCustomResponseDTO(
                p.getId(),
                p.getPaciente().getId(),
                p.getEtiqueta(),
                p.getImagenUrl(),
                p.getCreadoEn()
        );
    }
}