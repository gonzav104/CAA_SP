package com.caa.api.services.impl;

import com.caa.api.dtos.MaterializarPictogramaDTO;
import com.caa.api.dtos.MaterializarResultadoDTO;
import com.caa.api.dtos.PictogramaGlobalResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.services.PictogramaGlobalService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PictogramaGlobalServiceImpl implements PictogramaGlobalService {

    private static final String ARASAAC_IMAGE_TEMPLATE =
            "https://static.arasaac.org/pictograms/%d/%d_300.png";

    private final PictogramaGlobalRepository pictogramaGlobalRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PictogramaGlobalResponseDTO> obtenerTodos() {
        return pictogramaGlobalRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PictogramaGlobalResponseDTO obtenerPorId(UUID id) {
        PictogramaGlobal p = pictogramaGlobalRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pictograma global no encontrado"));
        return toResponseDTO(p);
    }

    @Override
    @Transactional
    public MaterializarResultadoDTO materializar(MaterializarPictogramaDTO dto) {
        // Chequeo proactivo: si ya existe por arasaacId, devolverlo (idempotente → dedupe hit)
        return pictogramaGlobalRepository.findByArasaacId(dto.arasaacId())
                .map(existente -> {
                    log.info("Materializar: arasaacId {} ya existe (uuid={}), devolviendo existente",
                            dto.arasaacId(), existente.getId());
                    return new MaterializarResultadoDTO(toResponseDTO(existente), false);
                })
                .orElseGet(() -> crearNuevo(dto));
    }

    private MaterializarResultadoDTO crearNuevo(MaterializarPictogramaDTO dto) {
        String imagenUrl = String.format(ARASAAC_IMAGE_TEMPLATE, dto.arasaacId(), dto.arasaacId());

        PictogramaGlobal nuevo = PictogramaGlobal.builder()
                .etiqueta(dto.etiqueta())
                .imagenUrl(imagenUrl)
                .arasaacId(dto.arasaacId())
                .build();

        try {
            PictogramaGlobal guardado = pictogramaGlobalRepository.save(nuevo);
            log.info("Materializar: nuevo pictograma global creado (arasaacId={}, uuid={})",
                    dto.arasaacId(), guardado.getId());
            return new MaterializarResultadoDTO(toResponseDTO(guardado), true);
        } catch (DataIntegrityViolationException e) {
            // Race condition: otro request creó el mismo arasaacId entre el chequeo y el save
            log.warn("Materializar: conflicto de duplicado para arasaacId={}, buscando existente",
                    dto.arasaacId());
            PictogramaGlobal existente = pictogramaGlobalRepository.findByArasaacId(dto.arasaacId())
                    .orElseThrow(() -> e);
            return new MaterializarResultadoDTO(toResponseDTO(existente), false);
        }
    }

    private PictogramaGlobalResponseDTO toResponseDTO(PictogramaGlobal p) {
        return new PictogramaGlobalResponseDTO(
                p.getId(),
                p.getEtiqueta(),
                p.getImagenUrl(),
                p.getArasaacId(),
                p.getCreadoEn()
        );
    }
}
