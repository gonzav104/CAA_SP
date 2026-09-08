package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.GoogleAuthResponseDTO;
import com.caa.api.dtos.GoogleCompletarRegistroDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.dtos.RestablecerPasswordDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.exceptions.DemasiadosIntentosException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AuthService.GoogleLoginResult;
import com.caa.api.services.GoogleTokenVerifier.GoogleUsuario;
import com.caa.api.services.RateLimitService.Operacion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Tests unitarios")
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    @Mock
    private EmailService emailService;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioExistente;

    @BeforeEach
    void setUp() {
        usuarioExistente = Usuario.builder()
                .id(UUID.randomUUID())
                .email("test@ejemplo.com")
                .passwordHash("$2a$10$hashedPasswordReal")
                .nombre("Test User")
                .rol(RolUsuario.TERAPEUTA)
                .build();
    }

    // ──────────────────────────────────────────────
    //  SAD PATHS — Login tradicional
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Login con email inexistente → lanza CredencialesInvalidasException")
    void login_emailInexistente_lanzaExcepcion() {
        LoginRequestDTO dto = new LoginRequestDTO("noexiste@ejemplo.com", "cualquiera");
        given(usuarioRepository.findByEmail("noexiste@ejemplo.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    @DisplayName("Login con password incorrecta → lanza CredencialesInvalidasException")
    void login_passwordIncorrecta_lanzaExcepcion() {
        LoginRequestDTO dto = new LoginRequestDTO("test@ejemplo.com", "passwordMala");
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(passwordEncoder.matches("passwordMala", usuarioExistente.getPasswordHash()))
                .willReturn(false);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(jwtService, never()).generarToken(any());
    }

    @Test
    @DisplayName("Login correcto → devuelve AuthResponseDTO con token")
    void login_correcto_devuelveToken() {
        LoginRequestDTO dto = new LoginRequestDTO("test@ejemplo.com", "passwordBuena");
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(passwordEncoder.matches("passwordBuena", usuarioExistente.getPasswordHash()))
                .willReturn(true);
        given(jwtService.generarToken(usuarioExistente))
                .willReturn("jwt-token-falso");

        AuthResponseDTO response = authService.login(dto);

        assertThat(response.token()).isEqualTo("jwt-token-falso");
        assertThat(response.tipo()).isEqualTo("Bearer");
        verify(jwtService).generarToken(usuarioExistente);
    }

    // ──────────────────────────────────────────────
    //  LOGIN CON GOOGLE — comportamiento viejo/nuevo
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Login con Google (usuario EXISTENTE) → requiereRol=false, token presente, no crea usuario")
    void loginConGoogle_usuarioExistente_devuelveToken() {
        given(googleTokenVerifier.verificar("id-token-valido"))
                .willReturn(Optional.of(new GoogleUsuario("test@ejemplo.com", "Test User")));
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(jwtService.generarToken(usuarioExistente))
                .willReturn("jwt-token-google");

        GoogleLoginResult result = authService.loginConGoogle("id-token-valido");

        assertThat(result.dto().requiereRol()).isFalse();
        assertThat(result.dto().tipo()).isEqualTo("Bearer");
        assertThat(result.dto().email()).isNull();
        assertThat(result.dto().nombre()).isNull();
        assertThat(result.token()).isPresent().contains("jwt-token-google");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login con Google (usuario NO existente) → requiereRol=true, sin token, SIN crear usuario")
    void loginConGoogle_usuarioNuevo_pideRol() {
        given(googleTokenVerifier.verificar("id-token-valido"))
                .willReturn(Optional.of(new GoogleUsuario("nuevo@ejemplo.com", "Nuevo Usuario")));
        given(usuarioRepository.findByEmail("nuevo@ejemplo.com"))
                .willReturn(Optional.empty());

        GoogleLoginResult result = authService.loginConGoogle("id-token-valido");

        assertThat(result.dto().requiereRol()).isTrue();
        assertThat(result.dto().tipo()).isNull();
        assertThat(result.dto().email()).isEqualTo("nuevo@ejemplo.com");
        assertThat(result.dto().nombre()).isEqualTo("Nuevo Usuario");
        assertThat(result.token()).isEmpty();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login con Google con TOKEN INVÁLIDO → lanza CredencialesInvalidasException")
    void loginConGoogle_tokenInvalido_lanzaExcepcion() {
        given(googleTokenVerifier.verificar("id-token-invalido"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginConGoogle("id-token-invalido"))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(usuarioRepository, never()).findByEmail(anyString());
        verify(usuarioRepository, never()).save(any());
        verify(jwtService, never()).generarToken(any());
    }

    // ──────────────────────────────────────────────
    //  COMPLETAR REGISTRO GOOGLE
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("completarRegistroGoogle con rol FAMILIAR → crea usuario con rol FAMILIAR (nunca TERAPEUTA)")
    void completarRegistroGoogle_conRolFAMILIAR_creaUsuarioConEseRol() {
        GoogleCompletarRegistroDTO dto = new GoogleCompletarRegistroDTO("id-token-nuevo", RolUsuario.FAMILIAR);
        given(googleTokenVerifier.verificar("id-token-nuevo"))
                .willReturn(Optional.of(new GoogleUsuario("nuevo@ejemplo.com", "Nuevo Usuario")));
        given(usuarioRepository.findByEmail("nuevo@ejemplo.com"))
                .willReturn(Optional.empty());
        given(usuarioRepository.save(any(Usuario.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(jwtService.generarToken(any(Usuario.class)))
                .willReturn("jwt-token-familiar");

        GoogleLoginResult result = authService.completarRegistroGoogle(dto);

        // Verificar que se creó el usuario con el rol FAMILIAR
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario creado = captor.getValue();
        assertThat(creado.getRol()).isEqualTo(RolUsuario.FAMILIAR);
        assertThat(creado.getEmail()).isEqualTo("nuevo@ejemplo.com");
        // Un usuario recién creado arranca con tokenVersion=0 (@Builder.Default)
        assertThat(creado.getTokenVersion()).isZero();
        verify(emailService).enviarBienvenida(creado);

        // Respuesta
        assertThat(result.dto().requiereRol()).isFalse();
        assertThat(result.dto().tipo()).isEqualTo("Bearer");
        assertThat(result.token()).isPresent().contains("jwt-token-familiar");
    }

    @Test
    @DisplayName("completarRegistroGoogle con rol TERAPEUTA → crea usuario con rol TERAPEUTA")
    void completarRegistroGoogle_conRolTERAPEUTA_creaUsuarioConEseRol() {
        GoogleCompletarRegistroDTO dto = new GoogleCompletarRegistroDTO("id-token-terapeuta", RolUsuario.TERAPEUTA);
        given(googleTokenVerifier.verificar("id-token-terapeuta"))
                .willReturn(Optional.of(new GoogleUsuario("terapeuta@ejemplo.com", "Dr. Smith")));
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com"))
                .willReturn(Optional.empty());
        given(usuarioRepository.save(any(Usuario.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(jwtService.generarToken(any(Usuario.class)))
                .willReturn("jwt-token-terapeuta");

        GoogleLoginResult result = authService.completarRegistroGoogle(dto);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getRol()).isEqualTo(RolUsuario.TERAPEUTA);
        assertThat(result.dto().requiereRol()).isFalse();
        assertThat(result.token()).isPresent();
    }

    @Test
    @DisplayName("completarRegistroGoogle — caso de carrera: usuario ya existe → loguea sin duplicar")
    void completarRegistroGoogle_casoCarrera_logueaSinDuplicar() {
        GoogleCompletarRegistroDTO dto = new GoogleCompletarRegistroDTO("id-token-doble", RolUsuario.FAMILIAR);
        given(googleTokenVerifier.verificar("id-token-doble"))
                .willReturn(Optional.of(new GoogleUsuario("carrera@ejemplo.com", "Carrera")));
        // El usuario ya fue creado por otro thread/pestaña
        given(usuarioRepository.findByEmail("carrera@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(jwtService.generarToken(usuarioExistente))
                .willReturn("jwt-token-carrera");

        GoogleLoginResult result = authService.completarRegistroGoogle(dto);

        // Login con la cuenta existente, no crea duplicado
        assertThat(result.dto().requiereRol()).isFalse();
        assertThat(result.dto().tipo()).isEqualTo("Bearer");
        assertThat(result.token()).isPresent().contains("jwt-token-carrera");
        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).enviarBienvenida(any());
    }

    @Test
    @DisplayName("completarRegistroGoogle con TOKEN INVÁLIDO → lanza CredencialesInvalidasException")
    void completarRegistroGoogle_tokenInvalido_lanzaExcepcion() {
        GoogleCompletarRegistroDTO dto = new GoogleCompletarRegistroDTO("token-malo", RolUsuario.TERAPEUTA);
        given(googleTokenVerifier.verificar("token-malo"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.completarRegistroGoogle(dto))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(usuarioRepository, never()).save(any());
        verify(jwtService, never()).generarToken(any());
    }

    // ──────────────────────────────────────────────
    //  RECUPERO DE CONTRASEÑA
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("olvidePassword con email EXISTENTE → guarda el HASH del token (no el plano) con expiración y envía el token plano por email")
    void olvidePassword_emailExistente_guardaTokenYEnviaEmail() {
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));

        authService.olvidePassword("test@ejemplo.com");

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario guardado = usuarioCaptor.getValue();

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarRecuperacionPassword(any(Usuario.class), tokenCaptor.capture());

        String tokenPlano = tokenCaptor.getValue();
        assertThat(tokenPlano).isNotBlank();
        // En BD se guarda SOLO el hash SHA-256: el token plano nunca se persiste.
        assertThat(guardado.getResetToken()).isNotBlank();
        assertThat(guardado.getResetToken()).isNotEqualTo(tokenPlano);
        assertThat(guardado.getResetToken()).isEqualTo(hashSha256(tokenPlano));
        assertThat(guardado.getResetTokenExpira())
                .isAfter(LocalDateTime.now())
                .isBefore(LocalDateTime.now().plusHours(2));
    }

    @Test
    @DisplayName("olvidePassword con email INEXISTENTE → no guarda nada ni envía email (éxito silencioso)")
    void olvidePassword_emailInexistente_noHaceNada() {
        given(usuarioRepository.findByEmail("noexiste@ejemplo.com"))
                .willReturn(Optional.empty());

        authService.olvidePassword("noexiste@ejemplo.com");

        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).enviarRecuperacionPassword(any(), anyString());
    }

    @Test
    @DisplayName("restablecerPassword con token VÁLIDO → busca por el HASH, cambia la contraseña, limpia el token y sube tokenVersion")
    void restablecerPassword_tokenValido_cambiaPasswordYLimpiarToken() {
        String token = "token-valido";
        // El token se guardó hasheado (olvidePassword nunca persiste el plano)
        usuarioExistente.setResetToken(hashSha256(token));
        usuarioExistente.setResetTokenExpira(LocalDateTime.now().plusHours(1));
        given(usuarioRepository.findByResetToken(hashSha256(token)))
                .willReturn(Optional.of(usuarioExistente));
        given(passwordEncoder.encode("NuevaClave1!")).willReturn("$2a$10$hashNuevo");

        authService.restablecerPassword(new RestablecerPasswordDTO(token, "NuevaClave1!"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());

        Usuario guardado = captor.getValue();
        assertThat(guardado.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
        assertThat(guardado.getResetToken()).isNull();
        assertThat(guardado.getResetTokenExpira()).isNull();
        // Restablecer la contraseña invalida TODAS las sesiones activas
        assertThat(guardado.getTokenVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("restablecerPassword con token EXPIRADO → error genérico, no cambia la contraseña ni la tokenVersion")
    void restablecerPassword_tokenExpirado_errorGenerico() {
        String token = "token-expirado";
        usuarioExistente.setResetToken(hashSha256(token));
        usuarioExistente.setResetTokenExpira(LocalDateTime.now().minusHours(1));
        given(usuarioRepository.findByResetToken(hashSha256(token)))
                .willReturn(Optional.of(usuarioExistente));

        assertThatThrownBy(() -> authService.restablecerPassword(
                new RestablecerPasswordDTO(token, "NuevaClave1!")))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El enlace no es válido o ha expirado");

        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("restablecerPassword con token INEXISTENTE → el MISMO error genérico que el expirado")
    void restablecerPassword_tokenInexistente_mismoErrorQueExpirado() {
        // El lookup interno es por el hash del token recibido
        given(usuarioRepository.findByResetToken(hashSha256("token-inexistente")))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.restablecerPassword(
                new RestablecerPasswordDTO("token-inexistente", "NuevaClave1!")))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El enlace no es válido o ha expirado");
    }

    // ──────────────────────────────────────────────
    //  RATE LIMIT (anti fuerza bruta)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("login con rate limit superado → lanza DemasiadosIntentosException ANTES de tocar el repositorio")
    void login_superaLimite_lanzaDemasiadosIntentos() {
        LoginRequestDTO dto = new LoginRequestDTO("test@ejemplo.com", "cualquiera");
        doThrow(new DemasiadosIntentosException())
                .when(rateLimitService)
                .verificarYConsumir(Operacion.LOGIN, "test@ejemplo.com");

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(DemasiadosIntentosException.class);

        // El chequeo ocurre ANTES de validar credenciales/existencia
        verify(usuarioRepository, never()).findByEmail(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    @DisplayName("olvidePassword con rate limit superado → lanza DemasiadosIntentosException ANTES de verificar el email")
    void olvidePassword_superaLimite_lanzaDemasiadosIntentos() {
        doThrow(new DemasiadosIntentosException())
                .when(rateLimitService)
                .verificarYConsumir(Operacion.OLVIDE_PASSWORD, "test@ejemplo.com");

        assertThatThrownBy(() -> authService.olvidePassword("test@ejemplo.com"))
                .isInstanceOf(DemasiadosIntentosException.class);

        verify(usuarioRepository, never()).findByEmail(anyString());
        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).enviarRecuperacionPassword(any(), anyString());
    }

    @Test
    @DisplayName("login consume cupo de rate limit antes de validar credenciales (fallido y exitoso cuentan)")
    void login_consumeCupoRateLimit_antesDeValidarCredenciales() {
        LoginRequestDTO dto = new LoginRequestDTO("test@ejemplo.com", "passwordMala");
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(passwordEncoder.matches("passwordMala", usuarioExistente.getPasswordHash()))
                .willReturn(false);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(rateLimitService).verificarYConsumir(Operacion.LOGIN, "test@ejemplo.com");
    }

    // ──────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────

    /**
     * Misma codificación que AuthService.hashResetToken: SHA-256 en hex.
     * Si el helper de producción cambiara, estos asserts fallarían.
     */
    private static String hashSha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}