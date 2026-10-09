package com.caa.api.controllers;

import com.caa.api.dtos.PacienteActualizacionDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.services.PacienteService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;

    // NOTA (tarea 3.7c): el antiguo POST /api/pacientes (sin organización, RolUsuario) fue
    // REMOVIDO. El único camino de creación es ahora
    // POST /api/organizaciones/{organizacionId}/pacientes (OrganizacionPacienteController,
    // introducido en la Fase 2). Esto es una ruptura de contrato documentada y confirmada por
    // la spec (capability patient-access-control, MODIFIED) — impacto en frontend: cualquier
    // cliente que todavía llame POST /api/pacientes debe migrar al endpoint org-scoped.

    @GetMapping
    public ResponseEntity<List<PacienteResponseDTO>> obtenerMisPacientes(Principal principal) {
        List<PacienteResponseDTO> pacientes = pacienteService.obtenerMisPacientes(principal.getName());
        return ResponseEntity.ok(pacientes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PacienteResponseDTO> obtenerPaciente(
            @PathVariable UUID id,
            Principal principal) {
        PacienteResponseDTO response = pacienteService.obtenerPaciente(id, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PacienteResponseDTO> actualizarPaciente(
            @PathVariable UUID id,
            @Valid @RequestBody PacienteActualizacionDTO dto,
            Principal principal) {
        PacienteResponseDTO response = pacienteService.actualizarPaciente(id, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPaciente(
            @PathVariable UUID id,
            Principal principal) {
        pacienteService.eliminarPaciente(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
