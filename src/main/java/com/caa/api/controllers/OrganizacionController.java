package com.caa.api.controllers;

import com.caa.api.dtos.OrganizacionActualizacionDTO;
import com.caa.api.dtos.OrganizacionRegistroDTO;
import com.caa.api.dtos.OrganizacionResponseDTO;
import com.caa.api.dtos.TransferenciaPropiedadDTO;
import com.caa.api.services.OrganizacionService;
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

/**
 * CRUD de {@code Organizacion} (design §12.2). La tenencia es siempre explícita en la ruta y se
 * revalida en cada llamada vía {@code AccesoService} — nunca se infiere de sesión/JWT.
 */
@RestController
@RequestMapping("/api/organizaciones")
@RequiredArgsConstructor
public class OrganizacionController {

    private final OrganizacionService organizacionService;

    /** Crea un espacio de trabajo (Organizacion + Membresia OWNER) en una sola transacción. */
    @PostMapping
    public ResponseEntity<OrganizacionResponseDTO> crear(
            @Valid @RequestBody OrganizacionRegistroDTO dto,
            Principal principal) {
        OrganizacionResponseDTO response = organizacionService.crear(dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Organizaciones donde el usuario autenticado tiene una Membresia. */
    @GetMapping
    public ResponseEntity<List<OrganizacionResponseDTO>> listarMisOrganizaciones(Principal principal) {
        return ResponseEntity.ok(organizacionService.listarMisOrganizaciones(principal.getName()));
    }

    /** No-miembro → 404 genérico (no revela si la organización existe). */
    @GetMapping("/{organizacionId}")
    public ResponseEntity<OrganizacionResponseDTO> obtener(
            @PathVariable UUID organizacionId,
            Principal principal) {
        return ResponseEntity.ok(organizacionService.obtener(organizacionId, principal.getName()));
    }

    /** Renombrar: rolGestion OWNER o ADMIN. */
    @PutMapping("/{organizacionId}")
    public ResponseEntity<OrganizacionResponseDTO> renombrar(
            @PathVariable UUID organizacionId,
            @Valid @RequestBody OrganizacionActualizacionDTO dto,
            Principal principal) {
        return ResponseEntity.ok(organizacionService.renombrar(organizacionId, dto, principal.getName()));
    }

    /** Solo OWNER; bloqueado (409) mientras existan pacientes en la organización. */
    @DeleteMapping("/{organizacionId}")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID organizacionId,
            Principal principal) {
        organizacionService.eliminar(organizacionId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * Transferencia de propiedad (design part 1 "Ownership Transfer Is Atomic, Concurrency-Safe,
     * and Leaves esTerapeuta Untouched"): solo OWNER; el destino debe ser miembro activo de la
     * misma organización.
     */
    @PostMapping("/{organizacionId}/transferencia-propiedad")
    public ResponseEntity<Void> transferirPropiedad(
            @PathVariable UUID organizacionId,
            @Valid @RequestBody TransferenciaPropiedadDTO dto,
            Principal principal) {
        organizacionService.transferirPropiedad(organizacionId, dto, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
