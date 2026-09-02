package com.caa.api.controllers;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaDetalleResponseDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.services.CartillaService;
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
@RequestMapping("/api/pacientes/{pacienteId}/cartillas")
@RequiredArgsConstructor
public class CartillaController {

    private final CartillaService cartillaService;

    @PostMapping
    public ResponseEntity<CartillaResponseDTO> crearCartilla(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody CartillaRegistroDTO dto,
            Principal principal) {
        CartillaResponseDTO response = cartillaService.crearCartilla(pacienteId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CartillaResponseDTO>> obtenerCartillas(
            @PathVariable UUID pacienteId,
            Principal principal) {
        List<CartillaResponseDTO> cartillas = cartillaService.obtenerCartillasDePaciente(pacienteId, principal.getName());
        return ResponseEntity.ok(cartillas);
    }

    @GetMapping("/{cartillaId}")
    public ResponseEntity<CartillaDetalleResponseDTO> obtenerCartillaDetalle(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            Principal principal) {
        CartillaDetalleResponseDTO detalle = cartillaService.obtenerCartillaDetalle(
                pacienteId, cartillaId, principal.getName());
        return ResponseEntity.ok(detalle);
    }

    @PutMapping("/{cartillaId}")
    public ResponseEntity<CartillaResponseDTO> actualizarCartilla(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            @Valid @RequestBody CartillaActualizacionDTO dto,
            Principal principal) {
        CartillaResponseDTO response = cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{cartillaId}")
    public ResponseEntity<Void> eliminarCartilla(
            @PathVariable UUID pacienteId,
            @PathVariable UUID cartillaId,
            Principal principal) {
        cartillaService.eliminarCartilla(pacienteId, cartillaId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
