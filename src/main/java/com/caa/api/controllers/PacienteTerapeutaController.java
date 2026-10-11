package com.caa.api.controllers;

import com.caa.api.dtos.AsignacionTerapeutaDTO;
import com.caa.api.dtos.TerapeutaAsignadoResponseDTO;
import com.caa.api.services.PacienteTerapeutaService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asignación clínica terapeuta↔paciente (design-part2 §12.2, §13; tareas 5.14-5.16). La tenencia
 * (misma organización que el paciente) se revalida en cada llamada vía {@code AccesoService} —
 * nunca se infiere de sesión/JWT.
 */
@RestController
@RequestMapping("/api/pacientes/{pacienteId}/terapeutas")
@RequiredArgsConstructor
public class PacienteTerapeutaController {

    private final PacienteTerapeutaService pacienteTerapeutaService;

    /** {@code acceso.esEquipo()}: cualquier miembro de gestión o clínico asignado ve el roster. */
    @GetMapping
    public ResponseEntity<List<TerapeutaAsignadoResponseDTO>> listarAsignados(
            @PathVariable UUID pacienteId,
            Principal principal) {
        return ResponseEntity.ok(pacienteTerapeutaService.listarAsignados(pacienteId, principal.getName()));
    }

    /**
     * {@code ADMINISTRAR} (rolGestion OWNER/ADMIN): el objetivo debe ser miembro de la MISMA
     * organización del paciente con esTerapeuta=true; idempotente ante asignación duplicada.
     */
    @PostMapping
    public ResponseEntity<TerapeutaAsignadoResponseDTO> asignar(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody AsignacionTerapeutaDTO dto,
            Principal principal) {
        TerapeutaAsignadoResponseDTO response =
                pacienteTerapeutaService.asignar(pacienteId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** {@code ADMINISTRAR}: puede dejar al paciente sin terapeutas asignados (estado válido). */
    @DeleteMapping("/{usuarioId}")
    public ResponseEntity<Void> desasignar(
            @PathVariable UUID pacienteId,
            @PathVariable UUID usuarioId,
            Principal principal) {
        pacienteTerapeutaService.desasignar(pacienteId, usuarioId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
