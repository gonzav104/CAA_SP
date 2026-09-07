package com.caa.api.controllers;

import com.caa.api.dtos.SesionActualizacionDTO;
import com.caa.api.dtos.SesionRegistroDTO;
import com.caa.api.dtos.SesionResponseDTO;
import com.caa.api.services.SesionService;
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
@RequestMapping("/api/pacientes/{pacienteId}/sesiones")
@RequiredArgsConstructor
public class SesionController {

    private final SesionService sesionService;

    @PostMapping
    public ResponseEntity<SesionResponseDTO> registrarSesion(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody SesionRegistroDTO dto,
            Principal principal) {
        SesionResponseDTO response = sesionService.registrarSesion(pacienteId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SesionResponseDTO>> obtenerSesionesDePaciente(
            @PathVariable UUID pacienteId,
            Principal principal) {
        List<SesionResponseDTO> sesiones = sesionService.obtenerSesionesDePaciente(pacienteId, principal.getName());
        return ResponseEntity.ok(sesiones);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SesionResponseDTO> obtenerSesion(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            Principal principal) {
        SesionResponseDTO response = sesionService.obtenerSesion(pacienteId, id, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SesionResponseDTO> actualizarSesion(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            @Valid @RequestBody SesionActualizacionDTO dto,
            Principal principal) {
        SesionResponseDTO response = sesionService.actualizarSesion(pacienteId, id, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarSesion(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            Principal principal) {
        sesionService.eliminarSesion(pacienteId, id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
