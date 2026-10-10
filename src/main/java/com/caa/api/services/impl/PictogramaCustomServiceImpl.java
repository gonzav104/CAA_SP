package com.caa.api.services.impl;

import com.caa.api.dtos.PictogramaCustomActualizacionDTO;
import com.caa.api.dtos.PictogramaCustomRegistroDTO;
import com.caa.api.dtos.PictogramaCustomResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.CloudinaryService;
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
    private final UsuarioRepository usuarioRepository;
    private final ItemCartillaRepository itemCartillaRepository;
    private final CloudinaryService cloudinaryService;
    private final AccesoService accesoService;

    @Override
    @Transactional(readOnly = true)
    public List<PictogramaCustomResponseDTO> obtenerPictogramasDePaciente(UUID pacienteId, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Access derives from the patient and current tenant relationships.
        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER);

        return pictogramaCustomRepository.findByPaciente_Id(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PictogramaCustomResponseDTO obtenerPictograma(UUID pacienteId, UUID id, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER);

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

        // Access derives from the patient and current tenant relationships.
        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.EDITAR_CONTENIDO);
        Paciente paciente = acceso.paciente();

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

        // Access derives from the patient and current tenant relationships.
        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.EDITAR_CONTENIDO);

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

    /** Baja de pictograma custom: GESTION_CLINICA (acceso de equipo), ya no terapeuta-propietario
     * directo vía {@code findByIdAndTerapeutaId} (design-part2 §11.2, tarea 3.5). */
    @Override
    @Transactional
    public void eliminarPictograma(UUID pacienteId, UUID id, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

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
