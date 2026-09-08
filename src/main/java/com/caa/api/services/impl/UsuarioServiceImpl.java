package com.caa.api.services.impl;

import com.caa.api.dtos.UsuarioRegistroDTO;
import com.caa.api.dtos.UsuarioResponseDTO;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.EmailService;
import com.caa.api.services.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    public UsuarioResponseDTO registrarUsuario(UsuarioRegistroDTO dto) {
        if (usuarioRepository.findByEmail(dto.email()).isPresent()) {
            throw new ConflictoException("El email ya está registrado");
        }

        Usuario usuario = Usuario.builder()
                .email(dto.email())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .nombre(dto.nombre())
                .rol(dto.rol())
                .build();

        Usuario usuarioGuardado = usuarioRepository.save(usuario);

        // Efecto secundario: EmailServiceImpl nunca propaga excepciones.
        emailService.enviarBienvenida(usuarioGuardado);

        return new UsuarioResponseDTO(
                usuarioGuardado.getId(),
                usuarioGuardado.getEmail(),
                usuarioGuardado.getNombre(),
                usuarioGuardado.getRol(),
                usuarioGuardado.getCreadoEn()
        );
    }

    @Override
    public UsuarioResponseDTO obtenerPorEmail(String email) {
        // Mensaje genérico (regla del proyecto): nunca revelar si el usuario
        // no existe o si el token ya no corresponde a un usuario vigente.
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(CredencialesInvalidasException::new);

        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getCreadoEn()
        );
    }
}
