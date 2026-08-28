package com.caa.api.services.impl;

import com.caa.api.dtos.UsuarioRegistroDTO;
import com.caa.api.dtos.UsuarioResponseDTO;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UsuarioResponseDTO registrarUsuario(UsuarioRegistroDTO dto) {
        if (usuarioRepository.findByEmail(dto.email()).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        Usuario usuario = Usuario.builder()
                .email(dto.email())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .nombre(dto.nombre())
                .rol(dto.rol())
                .build();

        Usuario usuarioGuardado = usuarioRepository.save(usuario);

        return new UsuarioResponseDTO(
                usuarioGuardado.getId(),
                usuarioGuardado.getEmail(),
                usuarioGuardado.getNombre(),
                usuarioGuardado.getRol(),
                usuarioGuardado.getCreadoEn()
        );
    }
}
