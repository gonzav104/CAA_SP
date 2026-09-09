package com.caa.api.controllers;

import com.caa.api.dtos.MaterializarPictogramaDTO;
import com.caa.api.dtos.MaterializarResultadoDTO;
import com.caa.api.dtos.PictogramaGlobalResponseDTO;
import com.caa.api.services.PictogramaGlobalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pictogramas-globales")
@RequiredArgsConstructor
public class PictogramaGlobalController {

    private final PictogramaGlobalService pictogramaGlobalService;

    @GetMapping
    public ResponseEntity<List<PictogramaGlobalResponseDTO>> obtenerTodos() {
        return ResponseEntity.ok(pictogramaGlobalService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PictogramaGlobalResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pictogramaGlobalService.obtenerPorId(id));
    }

    @PostMapping("/materializar")
    public ResponseEntity<PictogramaGlobalResponseDTO> materializar(
            @Valid @RequestBody MaterializarPictogramaDTO dto) {
        MaterializarResultadoDTO resultado = pictogramaGlobalService.materializar(dto);
        // 201 = recién materializado; 200 = dedupe hit (idempotente, mismo UUID existente)
        HttpStatus status = resultado.recienCreado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resultado.pictograma());
    }
}
