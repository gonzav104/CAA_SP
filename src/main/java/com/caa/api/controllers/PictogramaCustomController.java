package com.caa.api.controllers;

import com.caa.api.dtos.PictogramaCustomActualizacionDTO;
import com.caa.api.dtos.PictogramaCustomRegistroDTO;
import com.caa.api.dtos.PictogramaCustomResponseDTO;
import com.caa.api.services.PictogramaCustomService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pacientes/{pacienteId}/pictogramas-custom")
@RequiredArgsConstructor
public class PictogramaCustomController {

    private final PictogramaCustomService pictogramaCustomService;

    @GetMapping
    public ResponseEntity<List<PictogramaCustomResponseDTO>> obtenerPictogramas(
            @PathVariable UUID pacienteId,
            Principal principal) {
        List<PictogramaCustomResponseDTO> pictogramas =
                pictogramaCustomService.obtenerPictogramasDePaciente(pacienteId, principal.getName());
        return ResponseEntity.ok(pictogramas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PictogramaCustomResponseDTO> obtenerPictograma(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            Principal principal) {
        PictogramaCustomResponseDTO pictograma =
                pictogramaCustomService.obtenerPictograma(pacienteId, id, principal.getName());
        return ResponseEntity.ok(pictograma);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PictogramaCustomResponseDTO> crearPictograma(
            @PathVariable UUID pacienteId,
            @Valid @ModelAttribute PictogramaCustomRegistroDTO dto,
            @RequestParam("archivo") MultipartFile archivo,
            Principal principal) {
        PictogramaCustomResponseDTO creado =
                pictogramaCustomService.crearPictograma(pacienteId, dto, archivo, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PictogramaCustomResponseDTO> actualizarPictograma(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            @ModelAttribute PictogramaCustomActualizacionDTO dto,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            Principal principal) {
        PictogramaCustomResponseDTO actualizado =
                pictogramaCustomService.actualizarPictograma(pacienteId, id, dto, archivo, principal.getName());
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPictograma(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            Principal principal) {
        pictogramaCustomService.eliminarPictograma(pacienteId, id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}