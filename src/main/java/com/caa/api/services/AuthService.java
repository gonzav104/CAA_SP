package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponseDTO login(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(CredencialesInvalidasException::new);

        if (!passwordEncoder.matches(dto.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        String token = jwtService.generarToken(usuario);
        return new AuthResponseDTO(token, "Bearer");
    }
}
