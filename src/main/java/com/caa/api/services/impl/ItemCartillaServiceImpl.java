package com.caa.api.services.impl;

import com.caa.api.dtos.ItemCartillaActualizacionDTO;
import com.caa.api.dtos.ItemCartillaRegistroDTO;
import com.caa.api.dtos.ItemCartillaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.ItemCartillaService;
import com.caa.api.services.PacienteService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemCartillaServiceImpl implements ItemCartillaService {

    private final ItemCartillaRepository itemCartillaRepository;
    private final CategoriaRepository categoriaRepository;
    private final CartillaRepository cartillaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PictogramaGlobalRepository pictogramaGlobalRepository;
    private final PictogramaCustomRepository pictogramaCustomRepository;
    private final PacienteService pacienteService;

    @Override
    @Transactional
    public ItemCartillaResponseDTO crearItem(UUID pacienteId, UUID cartillaId, UUID categoriaId,
                                             ItemCartillaRegistroDTO dto, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Ownership por creador: solo quien creó la cartilla puede agregarle items
        cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        Categoria categoria = categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada o no tiene permisos"));

        // Resuelve y valida el recurso (XOR global/custom)
        RecursoResuelto recurso = resolverRecurso(dto.recursoGlobalId(), dto.recursoCustomId(), pacienteId);

        int ordenVisual = dto.ordenVisual() != null
                ? dto.ordenVisual()
                : siguienteOrdenVisual(categoriaId);

        ItemCartilla item = ItemCartilla.builder()
                .categoria(categoria)
                .textoHablado(dto.textoHablado())
                .ordenVisual(ordenVisual)
                .recursoGlobal(recurso.global())
                .recursoCustom(recurso.custom())
                .build();

        ItemCartilla guardado = itemCartillaRepository.save(item);
        return toResponseDTO(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemCartillaResponseDTO> obtenerItemsDeCategoria(UUID pacienteId, UUID cartillaId,
                                                                 UUID categoriaId, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        pacienteService.pacienteLegibleParaUsuario(pacienteId, usuario);

        cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada o no tiene permisos"));

        return itemCartillaRepository.findByCategoriaIdOrderByOrdenVisualAsc(categoriaId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public ItemCartillaResponseDTO actualizarItem(UUID pacienteId, UUID cartillaId, UUID categoriaId, UUID itemId,
                                                  ItemCartillaActualizacionDTO dto, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Ownership por creador: solo quien creó la cartilla puede modificar sus items
        cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada o no tiene permisos"));

        ItemCartilla item = itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Item no encontrado o no tiene permisos"));

        RecursoResuelto recurso = resolverRecurso(dto.recursoGlobalId(), dto.recursoCustomId(), pacienteId);

        item.setTextoHablado(dto.textoHablado());
        if (dto.ordenVisual() != null) {
            item.setOrdenVisual(dto.ordenVisual());
        }
        item.setRecursoGlobal(recurso.global());
        item.setRecursoCustom(recurso.custom());

        ItemCartilla actualizado = itemCartillaRepository.save(item);
        return toResponseDTO(actualizado);
    }

    @Override
    @Transactional
    public void eliminarItem(UUID pacienteId, UUID cartillaId, UUID categoriaId, UUID itemId, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Ownership por creador: solo quien creó la cartilla puede eliminar sus items
        cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada o no tiene permisos"));

        ItemCartilla item = itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Item no encontrado o no tiene permisos"));

        itemCartillaRepository.delete(item);
    }

    /**
     * Regla de negocio: el item debe referenciar EXACTAMENTE UNO de recursoGlobal o recursoCustom.
     * Si referencia un custom, validá que pertenezca al mismo paciente dueño de la cartilla.
     */
    private RecursoResuelto resolverRecurso(UUID globalId, UUID customId, UUID pacienteId) {
        boolean tieneGlobal = globalId != null;
        boolean tieneCustom = customId != null;

        if (tieneGlobal == tieneCustom) { // ambos o ninguno
            throw new IllegalArgumentException(
                    "El item debe referenciar exactamente un recurso: recursoGlobalId O recursoCustomId (no ambos, no ninguno)");
        }

        if (tieneGlobal) {
            PictogramaGlobal global = pictogramaGlobalRepository.findById(globalId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Pictograma global no encontrado"));
            return new RecursoResuelto(global, null);
        }

        // Tiene custom: validar que pertenezca al mismo paciente
        PictogramaCustom custom = pictogramaCustomRepository.findById(customId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pictograma custom no encontrado"));

        if (!custom.getPaciente().getId().equals(pacienteId)) {
            throw new IllegalArgumentException(
                    "El pictograma custom no pertenece al paciente dueño de esta cartilla");
        }

        return new RecursoResuelto(null, custom);
    }

    private int siguienteOrdenVisual(UUID categoriaId) {
        List<ItemCartilla> existentes = itemCartillaRepository.findByCategoriaIdOrderByOrdenVisualAsc(categoriaId);
        return existentes.stream()
                .mapToInt(ItemCartilla::getOrdenVisual)
                .max()
                .orElse(-1) + 1;
    }

    private ItemCartillaResponseDTO toResponseDTO(ItemCartilla i) {
        return new ItemCartillaResponseDTO(
                i.getId(),
                i.getCategoria().getId(),
                i.getTextoHablado(),
                i.getOrdenVisual(),
                i.getRecursoGlobal() != null ? i.getRecursoGlobal().getId() : null,
                i.getRecursoCustom() != null ? i.getRecursoCustom().getId() : null,
                i.getCreadoEn()
        );
    }

    private record RecursoResuelto(PictogramaGlobal global, PictogramaCustom custom) {
    }
}
