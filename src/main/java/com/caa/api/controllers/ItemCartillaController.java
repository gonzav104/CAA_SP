package com.caa.api.controllers;

import com.caa.api.dtos.ItemCartillaActualizacionDTO;
import com.caa.api.dtos.ItemCartillaRegistroDTO;
import com.caa.api.dtos.ItemCartillaResponseDTO;
import com.caa.api.services.ItemCartillaService;
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
@RequestMapping("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias/{categoriaId}/items")
@RequiredArgsConstructor
public class ItemCartillaController {

    private final ItemCartillaService itemCartillaService;

    @PostMapping
    public ResponseEntity<ItemCartillaResponseDTO> crearItem(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @PathVariable UUID categoriaId,
            @Valid @RequestBody ItemCartillaRegistroDTO dto,
            Principal principal) {
        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ItemCartillaResponseDTO>> obtenerItems(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @PathVariable UUID categoriaId,
            Principal principal) {
        List<ItemCartillaResponseDTO> items =
                itemCartillaService.obtenerItemsDeCategoria(pacienteId, cartillaId, categoriaId, principal.getName());
        return ResponseEntity.ok(items);
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ItemCartillaResponseDTO> actualizarItem(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @PathVariable UUID categoriaId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemCartillaActualizacionDTO dto,
            Principal principal) {
        ItemCartillaResponseDTO response =
                itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId, itemId, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> eliminarItem(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @PathVariable UUID categoriaId,
            @PathVariable UUID itemId,
            Principal principal) {
        itemCartillaService.eliminarItem(pacienteId, cartillaId, categoriaId, itemId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
