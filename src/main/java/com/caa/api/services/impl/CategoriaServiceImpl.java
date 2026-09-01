package com.caa.api.services.impl;

import com.caa.api.dtos.CategoriaActualizacionDTO;
import com.caa.api.dtos.CategoriaRegistroDTO;
import com.caa.api.dtos.CategoriaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.Paciente;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.CategoriaService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final CartillaRepository cartillaRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public CategoriaResponseDTO crearCategoria(UUID pacienteId, UUID cartillaId,
                                               CategoriaRegistroDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        int orden = dto.orden() != null
                ? dto.orden()
                : siguienteOrden(cartillaId);

        Categoria categoria = Categoria.builder()
                .cartilla(cartilla)
                .nombre(dto.nombre())
                .colorHex(dto.colorHex())
                .orden(orden)
                .build();

        Categoria guardada = categoriaRepository.save(categoria);
        return toResponseDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> obtenerCategoriasDeCartilla(UUID pacienteId, UUID cartillaId, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        return categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public CategoriaResponseDTO actualizarCategoria(UUID pacienteId, UUID cartillaId, UUID categoriaId,
                                                    CategoriaActualizacionDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        Categoria categoria = categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada o no tiene permisos"));

        categoria.setNombre(dto.nombre());
        categoria.setColorHex(dto.colorHex());
        if (dto.orden() != null) {
            categoria.setOrden(dto.orden());
        }

        Categoria actualizada = categoriaRepository.save(categoria);
        return toResponseDTO(actualizada);
    }

    @Override
    @Transactional
    public void eliminarCategoria(UUID pacienteId, UUID cartillaId, UUID categoriaId, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        Categoria categoria = categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada o no tiene permisos"));

        categoriaRepository.delete(categoria);
    }

    private int siguienteOrden(UUID cartillaId) {
        List<Categoria> existentes = categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId);
        return existentes.stream()
                .mapToInt(Categoria::getOrden)
                .max()
                .orElse(-1) + 1;
    }

    private CategoriaResponseDTO toResponseDTO(Categoria c) {
        return new CategoriaResponseDTO(
                c.getId(),
                c.getCartilla().getId(),
                c.getNombre(),
                c.getColorHex(),
                c.getOrden(),
                c.getCreadoEn()
        );
    }
}
