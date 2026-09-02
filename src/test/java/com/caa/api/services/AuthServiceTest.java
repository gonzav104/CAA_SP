package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.GoogleAuthResponseDTO;
import com.caa.api.dtos.GoogleCompletarRegistroDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AuthService.GoogleLoginResult;
import com.caa.api.services.GoogleTokenVerifier.GoogleUsuario;
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
}