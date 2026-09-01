package com.caa.api.controllers;

import com.caa.api.dtos.PictogramaGlobalResponseDTO;
import com.caa.api.services.PictogramaGlobalService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
