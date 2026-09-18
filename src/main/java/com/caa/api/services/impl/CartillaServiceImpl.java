package com.caa.api.services.impl;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaDetalleResponseDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.dtos.CategoriaDetalleResponseDTO;
import com.caa.api.dtos.ItemDetalleResponseDTO;
import com.caa.api.dtos.PictogramaInfoDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Paciente;
import com.caa.api.models.ParadigmaCartilla;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.CartillaService;
import com.caa.api.services.PacienteService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartillaServiceImpl implements CartillaService {

    private final CartillaRepository cartillaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ItemCartillaRepository itemCartillaRepository;
    private final PacienteService pacienteService;

    @Override
    @Transactional
    public CartillaResponseDTO crearCartilla(UUID pacienteId, CartillaRegistroDTO dto, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Gate de rol: terapeuta dueño del paciente o familiar con EDICION_LIMITADA
        pacienteService.verificarEdicionParaUsuario(pacienteId, usuario);
        Paciente paciente = pacienteService.pacienteLegibleParaUsuario(pacienteId, usuario);

        Cartilla cartilla = Cartilla.builder()
                .paciente(paciente)
                .creador(usuario)
                .nombre(dto.nombre())
                .esPrincipal(dto.esPrincipal() != null && dto.esPrincipal())
                .paradigma(dto.paradigma() != null ? dto.paradigma() : ParadigmaCartilla.TAXONOMICA)
                .build();

        Cartilla guardada = cartillaRepository.save(cartilla);
        return toResponseDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartillaResponseDTO> obtenerCartillasDePaciente(UUID pacienteId, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteService.pacienteLegibleParaUsuario(pacienteId, usuario);

        return cartillaRepository.findByPacienteId(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CartillaDetalleResponseDTO obtenerCartillaDetalle(UUID pacienteId, UUID cartillaId, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteService.pacienteLegibleParaUsuario(pacienteId, usuario);

        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        List<Categoria> categorias = categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId);

        Map<UUID, List<ItemDetalleResponseDTO>> itemsPorCategoria = new LinkedHashMap<>();
        for (Categoria c : categorias) {
            itemsPorCategoria.put(c.getId(), new ArrayList<>()); // categorías vacías sobreviven
        }
        if (!categorias.isEmpty()) { // evita un IN () innecesario
            List<UUID> categoriaIds = categorias.stream().map(Categoria::getId).toList();
            for (ItemCartilla item : itemCartillaRepository.findByCategoriaIdInOrderByOrdenVisualAsc(categoriaIds)) {
                List<ItemDetalleResponseDTO> bucket = itemsPorCategoria.get(item.getCategoria().getId());
                if (bucket != null) {
                    bucket.add(toItemDetalle(item));
                }
            }
        }

        List<CategoriaDetalleResponseDTO> categoriasDto = categorias.stream()
                .map(c -> new CategoriaDetalleResponseDTO(
                        c.getId(), c.getNombre(), c.getColorHex(), c.getOrden(),
                        itemsPorCategoria.get(c.getId())))
                .toList();

        return new CartillaDetalleResponseDTO(
                cartilla.getId(),
                cartilla.getCreador().getId(),
                cartilla.getNombre(),
                cartilla.isEsPrincipal(),
                cartilla.getParadigma(),
                categoriasDto
        );
    }

    @Override
    @Transactional
    public CartillaResponseDTO actualizarCartilla(UUID pacienteId, UUID cartillaId,
                                                  CartillaActualizacionDTO dto, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Ownership por creador: solo quien creó ESTA cartilla puede modificarla
        Cartilla cartilla = cartillaRepository
                .findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        cartilla.setNombre(dto.nombre());
        if (dto.esPrincipal() != null) {
            cartilla.setEsPrincipal(dto.esPrincipal());
        }
        if (dto.paradigma() != null) {
            cartilla.setParadigma(dto.paradigma());
        }

        Cartilla actualizada = cartillaRepository.save(cartilla);
        return toResponseDTO(actualizada);
    }

    @Override
    @Transactional
    public void eliminarCartilla(UUID pacienteId, UUID cartillaId, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Ownership por creador: solo quien creó ESTA cartilla puede eliminarla
        Cartilla cartilla = cartillaRepository
                .findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        cartillaRepository.delete(cartilla);
    }

    private CartillaResponseDTO toResponseDTO(Cartilla c) {
        return new CartillaResponseDTO(
                c.getId(),
                c.getPaciente().getId(),
                c.getCreador().getId(),
                c.getNombre(),
                c.isEsPrincipal(),
                c.getCreadoEn()
        );
    }

    private ItemDetalleResponseDTO toItemDetalle(ItemCartilla item) {
        return new ItemDetalleResponseDTO(
                item.getId(),
                item.getTextoHablado(),
                item.getOrdenVisual(),
                resolverPictograma(item)
        );
    }

    private PictogramaInfoDTO resolverPictograma(ItemCartilla item) {
        // El @EntityGraph de findByCategoriaIdInOrderByOrdenVisualAsc ya inicializó estas
        // asociaciones al devolver la lista, así que se leen directo del proxy sin findById.
        PictogramaGlobal global = item.getRecursoGlobal();
        if (global != null) {
            return new PictogramaInfoDTO(global.getId(), global.getEtiqueta(), global.getImagenUrl(), "GLOBAL");
        }

        PictogramaCustom custom = item.getRecursoCustom();
        if (custom != null) {
            return new PictogramaInfoDTO(custom.getId(), custom.getEtiqueta(), custom.getImagenUrl(), "CUSTOM");
        }

        // Rama defensiva: en producción es inalcanzable porque el CHECK check_origen_recurso
        // (init.sql) y el XOR de resolverRecurso (tieneGlobal == tieneCustom → throw) impiden
        // que un item quede sin ningún recurso asociado. Se conserva por defensa en profundidad.

        return null;
    }
}
