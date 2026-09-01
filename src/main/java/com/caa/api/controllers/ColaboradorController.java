package com.caa.api.controllers;

import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.services.ColaboradorService;
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
@RequestMapping("/api/pacientes/{pacienteId}/colaboradores")
@RequiredArgsConstructor
public class ColaboradorController {

    private final ColaboradorService colaboradorService;

    @PostMapping
    public ResponseEntity<ColaboradorResponseDTO> vincularColaborador(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody ColaboradorRegistroDTO dto,
            Principal principal) {
        ColaboradorResponseDTO response = colaboradorService.vincularColaborador(
                pacienteId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ColaboradorResponseDTO>> obtenerColaboradores(
            @PathVariable UUID pacienteId,
            Principal principal) {
        List<ColaboradorResponseDTO> colaboradores = colaboradorService.obtenerColaboradores(
                pacienteId, principal.getName());
        return ResponseEntity.ok(colaboradores);
    }

    @PutMapping("/{usuarioId}")
    public ResponseEntity<ColaboradorResponseDTO> actualizarPermiso(
            @PathVariable UUID pacienteId,
            @PathVariable UUID usuarioId,
            @Valid @RequestBody ColaboradorActualizacionDTO dto,
            Principal principal) {
        ColaboradorResponseDTO response = colaboradorService.actualizarPermiso(
                pacienteId, usuarioId, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{usuarioId}")
    public ResponseEntity<Void> revocarColaborador(
            @PathVariable UUID pacienteId,
            @PathVariable UUID usuarioId,
            Principal principal) {
        colaboradorService.revocarColaborador(pacienteId, usuarioId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
