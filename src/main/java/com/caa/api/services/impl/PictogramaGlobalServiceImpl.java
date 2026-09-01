package com.caa.api.services.impl;

import com.caa.api.dtos.PictogramaGlobalResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.services.PictogramaGlobalService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PictogramaGlobalServiceImpl implements PictogramaGlobalService {

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

    private PictogramaGlobalResponseDTO toResponseDTO(PictogramaGlobal p) {
        return new PictogramaGlobalResponseDTO(
                p.getId(),
                p.getEtiqueta(),
                p.getImagenUrl(),
                p.getCreadoEn()
        );
    }
}
