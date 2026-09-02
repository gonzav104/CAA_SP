package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.GoogleAuthResponseDTO;
import com.caa.api.dtos.GoogleCompletarRegistroDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.GoogleTokenVerifier.GoogleUsuario;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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

    /**
     * Login con Google: si el usuario ya existe, emite token (devuelto en el record).
     * Si no, pide que seleccione un rol (requiereRol=true, sin token).
     *
     * @return pair (GoogleAuthResponseDTO, token|null)
     */
    public GoogleLoginResult loginConGoogle(String idToken) {
        GoogleUsuario googleUsuario = googleTokenVerifier.verificar(idToken)
                .orElseThrow(CredencialesInvalidasException::new);

        return usuarioRepository.findByEmail(googleUsuario.email())
                .map(usuario -> {
                    String token = jwtService.generarToken(usuario);
                    return new GoogleLoginResult(
                            new GoogleAuthResponseDTO(false, "Bearer", null, null),
                            Optional.of(token));
                })
                .orElseGet(() -> new GoogleLoginResult(
                        new GoogleAuthResponseDTO(true, null, googleUsuario.email(), googleUsuario.nombre()),
                        Optional.empty()));
    }

    /**
     * Completar registro de un usuario nuevo de Google.
     * Re-verifica el idToken (nunca confiar en el email que manda el cliente).
     * Si el usuario ya fue creado (carrera/doble click), loguea sin duplicar.
     *
     * @return pair (GoogleAuthResponseDTO, token)
     */
    public GoogleLoginResult completarRegistroGoogle(GoogleCompletarRegistroDTO dto) {
        GoogleUsuario googleUsuario = googleTokenVerifier.verificar(dto.idToken())
                .orElseThrow(CredencialesInvalidasException::new);

        return usuarioRepository.findByEmail(googleUsuario.email())
                .map(usuario -> {
                    log.info("Usuario {} ya existía al completar registro Google — login directo",
                            googleUsuario.email());
                    String token = jwtService.generarToken(usuario);
                    return new GoogleLoginResult(
                            new GoogleAuthResponseDTO(false, "Bearer", null, null),
                            Optional.of(token));
                })
                .orElseGet(() -> {
                    Usuario nuevo = Usuario.builder()
                            .email(googleUsuario.email())
                            .nombre(googleUsuario.nombre())
                            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .rol(dto.rol())
                            .build();
                    Usuario guardado = usuarioRepository.save(nuevo);
                    String token = jwtService.generarToken(guardado);
                    return new GoogleLoginResult(
                            new GoogleAuthResponseDTO(false, "Bearer", null, null),
                            Optional.of(token));
                });
    }

    public record GoogleLoginResult(GoogleAuthResponseDTO dto, Optional<String> token) {
    }
}