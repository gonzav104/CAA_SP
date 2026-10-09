package com.caa.api.services.impl;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaDetalleResponseDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.dtos.CategoriaDetalleResponseDTO;
import com.caa.api.dtos.ItemDetalleResponseDTO;
import com.caa.api.dtos.PictogramaInfoDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
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
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.CartillaService;
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
    private final AccesoService accesoService;

    @Override
    @Transactional
    public CartillaResponseDTO crearCartilla(UUID pacienteId, CartillaRegistroDTO dto, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Gate de acceso: EDITAR_CONTENIDO (equipo o familiar con EDICION_LIMITADA) — design-part2 §11.2
        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.EDITAR_CONTENIDO);
        Paciente paciente = acceso.paciente();

        boolean esPrincipal = Boolean.TRUE.equals(dto.esPrincipal());
        if (esPrincipal) {
            // Permiso ANTES de cualquier escritura: solo un miembro de equipo fija la principal
            exigirEquipo(acceso);
            // ORDEN CRÍTICO: el UPDATE masivo debe llegar a la base ANTES de escribir la nueva
            // principal. Si hubiera un INSERT pendiente, Hibernate lo vaciaría (auto-flush) antes
            // del UPDATE sobre la misma tabla y el índice único parcial vería dos principales.
            cartillaRepository.desmarcarPrincipalesDe(pacienteId);
        }

        Cartilla cartilla = Cartilla.builder()
                .paciente(paciente)
                .creador(usuario)
                .nombre(dto.nombre())
                .esPrincipal(esPrincipal)
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

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER);

        return cartillaRepository.findByPacienteId(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CartillaDetalleResponseDTO obtenerCartillaDetalle(UUID pacienteId, UUID cartillaId, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER);

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

        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.EDITAR_CONTENIDO);
        // Acceso deriva del paciente, nunca de quién creó ESTA cartilla (design-part2 §11.2, R2)
        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        // Solo un CAMBIO real del valor es una operación de configuración del paciente;
        // reenviar el mismo valor no tiene efectos ni requiere permiso adicional.
        boolean cambiaPrincipal = dto.esPrincipal() != null && dto.esPrincipal() != cartilla.isEsPrincipal();
        if (cambiaPrincipal) {
            // Permiso ANTES de cualquier escritura (fijar la principal o quitar la actual)
            exigirEquipo(acceso);
            if (dto.esPrincipal()) {
                // ORDEN CRÍTICO: desmarcar las otras ANTES de tocar la entidad gestionada (incluido
                // setNombre) para que el flush posterior nunca escriba dos principales a la vez.
                // Se excluye esta cartilla: si ya estuviera gestionada con esPrincipal = true, un
                // UPDATE masivo que la incluyera la dejaría en false en la base sin que Hibernate
                // detecte cambio alguno.
                cartillaRepository.desmarcarOtrasPrincipalesDe(pacienteId, cartillaId);
            }
        }

        cartilla.setNombre(dto.nombre());
        if (cambiaPrincipal) {
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
    public CartillaResponseDTO establecerCartillaPrincipal(UUID pacienteId, UUID cartillaId, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // 1) Acceso al paciente (sin acceso → 404)
        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER);
        // 2) Solo un miembro de equipo (un familiar vinculado → 403)
        exigirEquipo(acceso);
        // 3) La cartilla debe pertenecer al paciente, sin importar quién la creó
        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        // Idempotente: si ya es la principal no se escribe nada
        if (cartilla.isEsPrincipal()) {
            return toResponseDTO(cartilla);
        }

        // ORDEN CRÍTICO (igual que en actualizarCartilla): el UPDATE masivo se ejecuta de inmediato y
        // debe llegar a la base ANTES de marcar esta cartilla, para que el índice único parcial
        // nunca vea dos principales a la vez.
        cartillaRepository.desmarcarOtrasPrincipalesDe(pacienteId, cartillaId);
        cartilla.setEsPrincipal(true);

        return toResponseDTO(cartillaRepository.save(cartilla));
    }

    @Override
    @Transactional
    public void eliminarCartilla(UUID pacienteId, UUID cartillaId, String emailTerapeuta) {
        Usuario usuario = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Acceso clínico (GESTION_CLINICA), nunca creador_id (design-part2 §11.2, R2)
        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);
        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cartilla no encontrada o no tiene permisos"));

        cartillaRepository.delete(cartilla);
    }

    /** Solo un miembro de equipo (gestión OWNER/ADMIN o clínico asignado) fija o quita la cartilla principal. */
    private void exigirEquipo(AccesoPaciente acceso) {
        if (!acceso.esEquipo()) {
            throw new AccesoDenegadoException(
                    "Solo un miembro del equipo del paciente puede establecer la cartilla principal");
        }
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
                resolverPictograma(item),
                item.isEsCore(),
                item.getTextoVisible(),
                item.isVisibleEnModoUso()
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
