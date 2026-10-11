package com.caa.api.controllers;

import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.services.PacienteService;
import jakarta.validation.Valid;
import java.security.Principal;
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

/**
 * Operaciones de paciente dentro de un workspace (design §12.2, part 1 §10.3). La tenencia es
 * explícita en la ruta y se revalida en cada llamada vía {@code AccesoService} — nunca se infiere
 * de sesión/JWT. Separado de {@link PacienteController} (que sigue atendiendo
 * {@code /api/pacientes/{pacienteId}}, cuyo tenant se deriva del propio recurso) porque las rutas
 * de organización están explícitamente anidadas bajo {@code /api/organizaciones} (design §1).
 */
@RestController
@RequestMapping("/api/organizaciones/{organizacionId}/pacientes")
@RequiredArgsConstructor
public class OrganizacionPacienteController {

    private final PacienteService pacienteService;

    /**
     * {@code POST}: exige {@code AccesoService.exigirAltaPaciente} (rolGestion OWNER/ADMIN O
     * esTerapeuta=true); auto-asigna al creador si {@code esTerapeuta=true} (design part 1 §10.3).
     */
    @PostMapping
    public ResponseEntity<PacienteResponseDTO> registrarPaciente(
            @PathVariable UUID organizacionId,
            @Valid @RequestBody PacienteRegistroDTO dto,
            Principal principal) {
        PacienteResponseDTO response = pacienteService.registrarPaciente(organizacionId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * {@code GET}: rolGestion OWNER/ADMIN ve el workspace completo; MIEMBRO con esTerapeuta=true
     * ve solo lo asignado; MIEMBRO con esTerapeuta=false ve una lista vacía (design-part2 §14).
     */
    @GetMapping
    public ResponseEntity<List<PacienteResponseDTO>> obtenerPacientesDeOrganizacion(
            @PathVariable UUID organizacionId,
            Principal principal) {
        List<PacienteResponseDTO> pacientes =
                pacienteService.obtenerPacientesDeOrganizacion(organizacionId, principal.getName());
        return ResponseEntity.ok(pacientes);
    }
}
