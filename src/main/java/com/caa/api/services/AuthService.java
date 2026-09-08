package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.GoogleAuthResponseDTO;
import com.caa.api.dtos.GoogleCompletarRegistroDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.dtos.RestablecerPasswordDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.GoogleTokenVerifier.GoogleUsuario;
import java.time.LocalDateTime;
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

    /**
     * Mensaje único para token inexistente y token expirado: nunca revelar
     * cuál de los dos casos es (regla del proyecto de no exponer información).
     */
    private static final String MENSAJE_ENLACE_INVALIDO = "El enlace no es válido o ha expirado";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final EmailService emailService;

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

                    // Efecto secundario: EmailServiceImpl nunca propaga excepciones.
                    emailService.enviarBienvenida(guardado);

                    String token = jwtService.generarToken(guardado);
                    return new GoogleLoginResult(
                            new GoogleAuthResponseDTO(false, "Bearer", null, null),
                            Optional.of(token));
                });
    }

    public record GoogleLoginResult(GoogleAuthResponseDTO dto, Optional<String> token) {
    }

    /**
     * Pide un enlace de recupero de contraseña para un email.
     * <p>
     * Nunca revela si el email está registrado: si no existe, se devuelve el
     * mismo resultado de éxito que si existiera.
     */
    public void olvidePassword(String email) {
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            String token = UUID.randomUUID().toString();
            // En BD se guarda el token plano; el mismo token viaja por email
            // para que el usuario lo use en el link de recupero.
            usuario.setResetToken(token);
            usuario.setResetTokenExpira(LocalDateTime.now().plusHours(1));
            usuarioRepository.save(usuario);

            emailService.enviarRecuperacionPassword(usuario, token);
        });
    }

    /**
     * Restablece la contraseña con un token de recupero válido.
     * <p>
     * Token inexistente y token expirado producen EXACTAMENTE el mismo error
     * genérico, para no revelar cuál de los dos casos es.
     */
    public void restablecerPassword(RestablecerPasswordDTO dto) {
        Usuario usuario = usuarioRepository.findByResetToken(dto.token())
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_ENLACE_INVALIDO));

        if (usuario.getResetTokenExpira() == null
                || usuario.getResetTokenExpira().isBefore(LocalDateTime.now())) {
            throw new RecursoNoEncontradoException(MENSAJE_ENLACE_INVALIDO);
        }

        usuario.setPasswordHash(passwordEncoder.encode(dto.password()));
        // El token es de un solo uso: se limpia para que no se pueda reutilizar.
        usuario.setResetToken(null);
        usuario.setResetTokenExpira(null);
        usuarioRepository.save(usuario);
    }
}