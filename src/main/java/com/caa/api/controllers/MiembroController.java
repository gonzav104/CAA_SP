package com.caa.api.controllers;

import com.caa.api.dtos.MiembroResponseDTO;
import com.caa.api.dtos.MiembroRolGestionDTO;
import com.caa.api.dtos.MiembroTerapeutaDTO;
import com.caa.api.services.OrganizacionService;
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

/**
 * Gestión de {@code Membresia} dentro de una organización (design §12.2). Separado de
 * {@link OrganizacionController} porque anida un sub-recurso ({@code /miembros/{usuarioId}})
 * con reglas de autorización propias por operación (mismo criterio que {@code ColaboradorController}
 * respecto de {@code PacienteController}).
 */
@RestController
@RequestMapping("/api/organizaciones/{organizacionId}/miembros")
@RequiredArgsConstructor
public class MiembroController {

    private final OrganizacionService organizacionService;

    /** Cualquier miembro de la organización. */
    @GetMapping
    public ResponseEntity<List<MiembroResponseDTO>> listarMiembros(
            @PathVariable UUID organizacionId,
            Principal principal) {
        return ResponseEntity.ok(organizacionService.listarMiembros(organizacionId, principal.getName()));
    }

    /** Solo OWNER; el objetivo nunca puede ser la propia fila del OWNER; nunca propone OWNER. */
    @PutMapping("/{usuarioId}/rol-gestion")
    public ResponseEntity<MiembroResponseDTO> cambiarRolGestion(
            @PathVariable UUID organizacionId,
            @PathVariable UUID usuarioId,
            @Valid @RequestBody MiembroRolGestionDTO dto,
            Principal principal) {
        MiembroResponseDTO response = organizacionService.cambiarRolGestion(
                organizacionId, usuarioId, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    /** OWNER sobre cualquier miembro (incl. sí mismo); ADMIN sobre cualquier miembro excepto el OWNER. */
    @PutMapping("/{usuarioId}/terapeuta")
    public ResponseEntity<MiembroResponseDTO> cambiarEsTerapeuta(
            @PathVariable UUID organizacionId,
            @PathVariable UUID usuarioId,
            @Valid @RequestBody MiembroTerapeutaDTO dto,
            Principal principal) {
        MiembroResponseDTO response = organizacionService.cambiarEsTerapeuta(
                organizacionId, usuarioId, dto, principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Baja de un miembro (OWNER/ADMIN) o salida voluntaria (el propio usuario con su mismo id).
     * El OWNER nunca puede ser eliminado ni puede salir sin transferir la propiedad antes (409).
     */
    @DeleteMapping("/{usuarioId}")
    public ResponseEntity<Void> eliminarMiembro(
            @PathVariable UUID organizacionId,
            @PathVariable UUID usuarioId,
            Principal principal) {
        organizacionService.eliminarMiembro(organizacionId, usuarioId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
