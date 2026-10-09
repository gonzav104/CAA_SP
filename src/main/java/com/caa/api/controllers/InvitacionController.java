package com.caa.api.controllers;

import com.caa.api.dtos.AceptarInvitacionDTO;
import com.caa.api.dtos.AceptarInvitacionResponseDTO;
import com.caa.api.dtos.InvitacionOrganizacionRegistroDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.services.InvitacionService;
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
 * Invitaciones a organización y familiares (design §9, §12.2). La creación siempre responde 202
 * con forma uniforme, sea el email destino ya tenga cuenta o no (no-enumeración).
 */
@RestController
@RequiredArgsConstructor
public class InvitacionController {

    private final InvitacionService invitacionService;

    // ──────────────────────────────────────────────
    //  Organización — rolGestion OWNER/ADMIN
    // ──────────────────────────────────────────────

    @PostMapping("/api/organizaciones/{organizacionId}/invitaciones")
    public ResponseEntity<InvitacionResponseDTO> crearOrganizacion(
            @PathVariable UUID organizacionId,
            @Valid @RequestBody InvitacionOrganizacionRegistroDTO dto,
            Principal principal) {
        InvitacionResponseDTO response = invitacionService.crearOrganizacion(organizacionId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/api/organizaciones/{organizacionId}/invitaciones")
    public ResponseEntity<List<InvitacionResponseDTO>> listarOrganizacion(
            @PathVariable UUID organizacionId,
            Principal principal) {
        return ResponseEntity.ok(invitacionService.listarOrganizacion(organizacionId, principal.getName()));
    }

    @DeleteMapping("/api/organizaciones/{organizacionId}/invitaciones/{invitacionId}")
    public ResponseEntity<Void> revocarOrganizacion(
            @PathVariable UUID organizacionId,
            @PathVariable UUID invitacionId,
            Principal principal) {
        invitacionService.revocarOrganizacion(organizacionId, invitacionId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────
    //  Familiar — Capacidad.GESTION_CLINICA sobre el paciente
    // ──────────────────────────────────────────────

    @GetMapping("/api/pacientes/{pacienteId}/invitaciones")
    public ResponseEntity<List<InvitacionResponseDTO>> listarFamiliar(
            @PathVariable UUID pacienteId,
            Principal principal) {
        return ResponseEntity.ok(invitacionService.listarFamiliar(pacienteId, principal.getName()));
    }

    @DeleteMapping("/api/pacientes/{pacienteId}/invitaciones/{invitacionId}")
    public ResponseEntity<Void> revocarFamiliar(
            @PathVariable UUID pacienteId,
            @PathVariable UUID invitacionId,
            Principal principal) {
        invitacionService.revocarFamiliar(pacienteId, invitacionId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────
    //  Aceptación — cualquier tipo
    // ──────────────────────────────────────────────

    @PostMapping("/api/invitaciones/aceptar")
    public ResponseEntity<AceptarInvitacionResponseDTO> aceptar(
            @Valid @RequestBody AceptarInvitacionDTO dto,
            Principal principal) {
        return ResponseEntity.ok(invitacionService.aceptar(dto, principal.getName()));
    }
}
