package com.caa.api.controllers;

import com.caa.api.dtos.CategoriaActualizacionDTO;
import com.caa.api.dtos.CategoriaRegistroDTO;
import com.caa.api.dtos.CategoriaResponseDTO;
import com.caa.api.services.CategoriaService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> crearCategoria(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @Valid @RequestBody CategoriaRegistroDTO dto,
            Principal principal) {
        CategoriaResponseDTO response = categoriaService.crearCategoria(pacienteId, cartillaId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> obtenerCategorias(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            Principal principal) {
        List<CategoriaResponseDTO> categorias = categoriaService.obtenerCategoriasDeCartilla(pacienteId, cartillaId, principal.getName());
        return ResponseEntity.ok(categorias);
    }

    @PutMapping("/{categoriaId}")
    public ResponseEntity<CategoriaResponseDTO> actualizarCategoria(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @PathVariable UUID categoriaId,
            @Valid @RequestBody CategoriaActualizacionDTO dto,
            Principal principal) {
        CategoriaResponseDTO response = categoriaService.actualizarCategoria(pacienteId, cartillaId, categoriaId, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{categoriaId}")
    public ResponseEntity<Void> eliminarCategoria(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @PathVariable UUID categoriaId,
            Principal principal) {
        categoriaService.eliminarCategoria(pacienteId, cartillaId, categoriaId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
