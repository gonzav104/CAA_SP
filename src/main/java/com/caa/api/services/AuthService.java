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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
    private final RateLimitService rateLimitService;

    public AuthResponseDTO login(LoginRequestDTO dto) {
        // Anti fuerza bruta: el chequeo va ANTES de validar credenciales.
        // Un login fallido consume cupo, y también uno exitoso.
        rateLimitService.verificarYConsumir(RateLimitService.Operacion.LOGIN, dto.email());

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
        // Anti fuerza bruta: el chequeo va ANTES de verificar si el email existe.
        rateLimitService.verificarYConsumir(RateLimitService.Operacion.OLVIDE_PASSWORD, email);

        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            String token = UUID.randomUUID().toString();
            // En BD se guarda SOLO el hash SHA-256 del token; el token plano viaja
            // por email (el usuario lo necesita para el link) y nunca se persiste.
            usuario.setResetToken(hashResetToken(token));
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
        // El token que llega por el link se hashea con la MISMA función que se
        // usó al guardarlo: se busca por el hash almacenado.
        Usuario usuario = usuarioRepository.findByResetToken(hashResetToken(dto.token()))
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_ENLACE_INVALIDO));

        if (usuario.getResetTokenExpira() == null
                || usuario.getResetTokenExpira().isBefore(LocalDateTime.now())) {
            throw new RecursoNoEncontradoException(MENSAJE_ENLACE_INVALIDO);
        }

        usuario.setPasswordHash(passwordEncoder.encode(dto.password()));
        // El token es de un solo uso: se limpia para que no se pueda reutilizar.
        usuario.setResetToken(null);
        usuario.setResetTokenExpira(null);
        // Invalida TODA sesión activa de la cuenta (cualquier dispositivo):
        // los JWT emitidos con la versión anterior dejan de autenticar.
        usuario.setTokenVersion((usuario.getTokenVersion() == null ? 0 : usuario.getTokenVersion()) + 1);
        usuarioRepository.save(usuario);
    }

    /**
     * SHA-256 en hex (minúsculas) del token plano. Helper único para ambos lados:
     * {@code olvidePassword} guarda el hash y {@code restablecerPassword} busca
     * por el hash — ambos usan EXACTAMENTE la misma codificación.
     */
    private static String hashResetToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en este JVM", e);
        }
    }
}