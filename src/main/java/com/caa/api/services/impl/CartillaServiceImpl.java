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
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.CartillaService;
import com.caa.api.services.PacienteService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartillaServiceImpl implements CartillaService {

    private final CartillaRepository cartillaRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ItemCartillaRepository itemCartillaRepository;
    private final PictogramaGlobalRepository pictogramaGlobalRepository;
    private final PictogramaCustomRepository pictogramaCustomRepository;
    private final PacienteService pacienteService;

    @Override
    @Transactional
    public CartillaResponseDTO crearCartilla(UUID pacienteId, CartillaRegistroDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        Cartilla cartilla = Cartilla.builder()
                .paciente(paciente)
                .nombre(dto.nombre())
                .esPrincipal(dto.esPrincipal() != null && dto.esPrincipal())
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

        List<CategoriaDetalleResponseDTO> categorias = categoriaRepository
                .findByCartillaIdOrderByOrdenAsc(cartillaId)
                .stream()
                .map(this::toCategoriaDetalle)
                .toList();

        return new CartillaDetalleResponseDTO(
                cartilla.getId(),
                cartilla.getNombre(),
                cartilla.isEsPrincipal(),
                categorias
        );
    }

    @Override
    @Transactional
    public CartillaResponseDTO actualizarCartilla(UUID pacienteId, UUID cartillaId,
                                                  CartillaActualizacionDTO dto, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Permite terapeuta propietario y familiar con EDICION_LIMITADA (igual que Categoria/Item)
        pacienteService.verificarEdicionParaUsuario(pacienteId, usuario);

        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        cartilla.setNombre(dto.nombre());
        if (dto.esPrincipal() != null) {
            cartilla.setEsPrincipal(dto.esPrincipal());
        }

        Cartilla actualizada = cartillaRepository.save(cartilla);
        return toResponseDTO(actualizada);
    }

    @Override
    @Transactional
    public void eliminarCartilla(UUID pacienteId, UUID cartillaId, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Terapeuta no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        cartillaRepository.delete(cartilla);
    }

    private CartillaResponseDTO toResponseDTO(Cartilla c) {
        return new CartillaResponseDTO(
                c.getId(),
                c.getPaciente().getId(),
                c.getNombre(),
                c.isEsPrincipal(),
                c.getCreadoEn()
        );
    }

    private CategoriaDetalleResponseDTO toCategoriaDetalle(Categoria categoria) {
        List<ItemDetalleResponseDTO> items = itemCartillaRepository
                .findByCategoriaIdOrderByOrdenVisualAsc(categoria.getId())
                .stream()
                .map(this::toItemDetalle)
                .toList();

        return new CategoriaDetalleResponseDTO(
                categoria.getId(),
                categoria.getNombre(),
                categoria.getColorHex(),
                categoria.getOrden(),
                items
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
        if (item.getRecursoGlobal() != null) {
            return pictogramaGlobalRepository.findById(item.getRecursoGlobal().getId())
                    .map(g -> new PictogramaInfoDTO(g.getId(), g.getEtiqueta(), g.getImagenUrl(), "GLOBAL"))
                    .orElse(null);
        }

        if (item.getRecursoCustom() != null) {
            return pictogramaCustomRepository.findById(item.getRecursoCustom().getId())
                    .map(c -> new PictogramaInfoDTO(c.getId(), c.getEtiqueta(), c.getImagenUrl(), "CUSTOM"))
                    .orElse(null);
        }

        return null;
    }
}
