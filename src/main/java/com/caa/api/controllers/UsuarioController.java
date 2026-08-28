package com.caa.api.controllers;

import com.caa.api.dtos.UsuarioRegistroDTO;
import com.caa.api.dtos.UsuarioResponseDTO;
import com.caa.api.services.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponseDTO> registrarUsuario(@Valid @RequestBody UsuarioRegistroDTO dto) {
        UsuarioResponseDTO response = usuarioService.registrarUsuario(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
