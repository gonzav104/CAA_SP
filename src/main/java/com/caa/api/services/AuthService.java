package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.GoogleAuthResponseDTO;
import com.caa.api.dtos.GoogleCompletarRegistroDTO;
import com.caa.api.dtos.GoogleRecuperacionCompletarDTO;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
public class AuthService {

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
    private final TokenSeguro tokenSeguro;

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

    /** Only an explicitly persisted Google subject can authenticate an existing identity. */
    public GoogleLoginResult loginConGoogle(String idToken) {
        GoogleUsuario google = googleTokenVerifier.verificar(idToken)
                .orElseThrow(CredencialesInvalidasException::new);
        Optional<Usuario> linked = usuarioRepository.findByGoogleSub(google.sub());
        if (linked.isPresent()) {
            return authenticateGoogle(linked.get(), google);
        }
        rejectExistingEmail(google.email());
        return new GoogleLoginResult(
                new GoogleAuthResponseDTO(true, null, google.email(), google.nombre()), Optional.empty());
    }

    @Transactional
    public GoogleLoginResult completarRegistroGoogle(GoogleCompletarRegistroDTO dto) {
        GoogleUsuario google = googleTokenVerifier.verificar(dto.idToken())
                .orElseThrow(CredencialesInvalidasException::new);
        Optional<Usuario> linked = usuarioRepository.findByGoogleSub(google.sub());
        if (linked.isPresent()) {
            return authenticateGoogle(linked.get(), google);
        }
        rejectExistingEmail(google.email());
        Usuario nuevo = Usuario.builder()
                .email(google.email()).googleSub(google.sub()).nombre(google.nombre())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString())).build();
        try {
            // Flush UNIQUE email/subject races before emitting credentials; never fall back to email.
            Usuario saved = usuarioRepository.saveAndFlush(nuevo);
            afterCommit(() -> emailService.enviarBienvenida(saved));
            return authenticateGoogle(saved, google);
        } catch (DataIntegrityViolationException conflict) {
            throw new CredencialesInvalidasException();
        }
    }

    private void rejectExistingEmail(String email) {
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new CredencialesInvalidasException();
        }
    }

    private GoogleLoginResult authenticateGoogle(Usuario usuario, GoogleUsuario google) {
        if (!google.sub().equals(usuario.getGoogleSub()) || !google.email().equals(usuario.getEmail())) {
            throw new CredencialesInvalidasException();
        }
        return new GoogleLoginResult(new GoogleAuthResponseDTO(false, "Bearer", null, null),
                Optional.of(jwtService.generarToken(usuario)));
    }

    /** Email-only legacy matches receive a challenge, never a login or an implicit binding. */
    @Transactional
    public void solicitarRecuperacionGoogle(String idToken) {
        GoogleUsuario google = googleTokenVerifier.verificar(idToken)
                .orElseThrow(CredencialesInvalidasException::new);
        rateLimitService.verificarYConsumir(RateLimitService.Operacion.OLVIDE_PASSWORD, google.email());
        Optional<Usuario> candidate = usuarioRepository.findLockedByEmail(google.email());
        if (candidate.isEmpty() || candidate.get().getGoogleSub() != null
                || usuarioRepository.findByGoogleSub(google.sub()).isPresent()) {
            return;
        }
        Usuario usuario = candidate.get();
        String token = tokenSeguro.generar();
        usuario.setGoogleRecoveryTokenHash(tokenSeguro.hash(token));
        usuario.setGoogleRecoveryExpiresAt(LocalDateTime.now().plusHours(1));
        usuario.setGoogleRecoverySub(google.sub());
        usuario.setGoogleRecoveryEmail(google.email());
        usuarioRepository.saveAndFlush(usuario);
        afterCommit(() -> emailService.enviarRecuperacionGoogle(usuario, token));
    }

    /** The row lock serializes consumption with reissue, ordinary reset and forgot-password. */
    @Transactional
    public GoogleLoginResult completarRecuperacionGoogle(GoogleRecuperacionCompletarDTO dto) {
        GoogleUsuario google = googleTokenVerifier.verificar(dto.idToken())
                .orElseThrow(CredencialesInvalidasException::new);
        if (dto.token() == null || dto.token().isBlank()) {
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = usuarioRepository.findLockedByGoogleRecoveryTokenHash(tokenSeguro.hash(dto.token()))
                .orElseThrow(CredencialesInvalidasException::new);
        if (usuario.getGoogleSub() != null || usuario.getGoogleRecoveryExpiresAt() == null
                || !usuario.getGoogleRecoveryExpiresAt().isAfter(LocalDateTime.now())
                || !google.sub().equals(usuario.getGoogleRecoverySub())
                || !google.email().equals(usuario.getGoogleRecoveryEmail())
                || !google.email().equals(usuario.getEmail())
                || usuarioRepository.findByGoogleSub(google.sub()).isPresent()
                || passwordEncoder.matches(dto.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        usuario.setGoogleSub(google.sub());
        usuario.setPasswordHash(passwordEncoder.encode(dto.password()));
        usuario.setTokenVersion((usuario.getTokenVersion() == null ? 0 : usuario.getTokenVersion()) + 1);
        usuario.setResetToken(null);
        usuario.setResetTokenExpira(null);
        clearGoogleRecovery(usuario);
        try {
            // UNIQUE subject conflicts roll back password, version and challenge together.
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException conflict) {
            throw new CredencialesInvalidasException();
        }
        // The controller receives this value only AFTER the transactional proxy commits.
        return authenticateGoogle(usuario, google);
    }

    private static void clearGoogleRecovery(Usuario usuario) {
        usuario.setGoogleRecoveryTokenHash(null);
        usuario.setGoogleRecoveryExpiresAt(null);
        usuario.setGoogleRecoverySub(null);
        usuario.setGoogleRecoveryEmail(null);
    }

    private static void afterCommit(Runnable notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notification.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notification.run();
            }
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
    @Transactional
    public void olvidePassword(String email) {
        // Anti fuerza bruta: el chequeo va ANTES de verificar si el email existe.
        rateLimitService.verificarYConsumir(RateLimitService.Operacion.OLVIDE_PASSWORD, email);

        usuarioRepository.findLockedByEmail(email).ifPresent(usuario -> {
            String token = UUID.randomUUID().toString();
            // En BD se guarda SOLO el hash SHA-256 del token; el token plano viaja
            // por email (el usuario lo necesita para el link) y nunca se persiste.
            usuario.setResetToken(tokenSeguro.hash(token));
            usuario.setResetTokenExpira(LocalDateTime.now().plusHours(1));
            usuarioRepository.save(usuario);

            afterCommit(() -> emailService.enviarRecuperacionPassword(usuario, token));
        });
    }

    /**
     * Restablece la contraseña con un token de recupero válido.
     * <p>
     * Token inexistente y token expirado producen EXACTAMENTE el mismo error
     * genérico, para no revelar cuál de los dos casos es.
     */
    @Transactional
    public void restablecerPassword(RestablecerPasswordDTO dto) {
        // El token que llega por el link se hashea con la MISMA función que se
        // usó al guardarlo: se busca por el hash almacenado.
        Usuario usuario = usuarioRepository.findLockedByResetToken(tokenSeguro.hash(dto.token()))
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_ENLACE_INVALIDO));

        if (usuario.getResetTokenExpira() == null
                || usuario.getResetTokenExpira().isBefore(LocalDateTime.now())) {
            throw new RecursoNoEncontradoException(MENSAJE_ENLACE_INVALIDO);
        }

        usuario.setPasswordHash(passwordEncoder.encode(dto.password()));
        clearGoogleRecovery(usuario);
        // El token es de un solo uso: se limpia para que no se pueda reutilizar.
        usuario.setResetToken(null);
        usuario.setResetTokenExpira(null);
        // Invalida TODA sesión activa de la cuenta (cualquier dispositivo):
        // los JWT emitidos con la versión anterior dejan de autenticar.
        usuario.setTokenVersion((usuario.getTokenVersion() == null ? 0 : usuario.getTokenVersion()) + 1);
        usuarioRepository.save(usuario);
    }
}
