package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.GoogleTokenVerifier.GoogleUsuario;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthResponseDTO login(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(CredencialesInvalidasException::new);

        if (!passwordEncoder.matches(dto.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        String token = jwtService.generarToken(usuario);
        return new AuthResponseDTO(token, "Bearer");
    }

    public AuthResponseDTO loginConGoogle(String idToken) {
        GoogleUsuario googleUsuario = googleTokenVerifier.verificar(idToken)
                .orElseThrow(CredencialesInvalidasException::new);

        Usuario usuario = usuarioRepository.findByEmail(googleUsuario.email())
                .orElseGet(() -> {
                    Usuario nuevo = Usuario.builder()
                            .email(googleUsuario.email())
                            .nombre(googleUsuario.nombre())
                            // Password aleatoria e inutilizable: el login con Google no usa password
                            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .rol(RolUsuario.TERAPEUTA)
                            .build();
                    return usuarioRepository.save(nuevo);
                });

        String token = jwtService.generarToken(usuario);
        return new AuthResponseDTO(token, "Bearer");
    }
}
