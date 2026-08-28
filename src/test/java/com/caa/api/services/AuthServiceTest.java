package com.caa.api.services;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    //  SAD PATHS — Casos de fallo (prioritarios)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Login con email inexistente → lanza CredencialesInvalidasException")
    void login_emailInexistente_lanzaExcepcion() {
        // Arrange
        LoginRequestDTO dto = new LoginRequestDTO("noexiste@ejemplo.com", "cualquiera");
        given(usuarioRepository.findByEmail("noexiste@ejemplo.com"))
                .willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    @DisplayName("Login con password incorrecta → lanza CredencialesInvalidasException")
    void login_passwordIncorrecta_lanzaExcepcion() {
        // Arrange
        LoginRequestDTO dto = new LoginRequestDTO("test@ejemplo.com", "passwordMala");
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(passwordEncoder.matches("passwordMala", usuarioExistente.getPasswordHash()))
                .willReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(jwtService, never()).generarToken(any());
    }

    @Test
    @DisplayName("Ambos fallos arrojan EXACTAMENTE la misma excepción (sin differenciar)")
    void login_fallosMismosArrojanMismaExcepcion() {
        // Arrange
        LoginRequestDTO dtoEmail = new LoginRequestDTO("noexiste@ejemplo.com", "x");
        LoginRequestDTO dtoPass = new LoginRequestDTO("test@ejemplo.com", "mala");

        given(usuarioRepository.findByEmail("noexiste@ejemplo.com"))
                .willReturn(Optional.empty());
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(passwordEncoder.matches("mala", usuarioExistente.getPasswordHash()))
                .willReturn(false);

        // Act
        CredencialesInvalidasException exEmail = org.junit.jupiter.api.Assertions.assertThrows(
                CredencialesInvalidasException.class, () -> authService.login(dtoEmail));
        CredencialesInvalidasException exPass = org.junit.jupiter.api.Assertions.assertThrows(
                CredencialesInvalidasException.class, () -> authService.login(dtoPass));

        // Assert — misma clase, mismo mensaje
        assertThat(exEmail).isExactlyInstanceOf(CredencialesInvalidasException.class);
        assertThat(exPass).isExactlyInstanceOf(CredencialesInvalidasException.class);
        assertThat(exEmail.getMessage()).isEqualTo(exPass.getMessage());
    }

    // ──────────────────────────────────────────────
    //  HAPPY PATH
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Login correcto → devuelve AuthResponseDTO con token")
    void login_correcto_devuelveToken() {
        // Arrange
        LoginRequestDTO dto = new LoginRequestDTO("test@ejemplo.com", "passwordBuena");
        given(usuarioRepository.findByEmail("test@ejemplo.com"))
                .willReturn(Optional.of(usuarioExistente));
        given(passwordEncoder.matches("passwordBuena", usuarioExistente.getPasswordHash()))
                .willReturn(true);
        given(jwtService.generarToken(usuarioExistente))
                .willReturn("jwt-token-falso");

        // Act
        AuthResponseDTO response = authService.login(dto);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token-falso");
        assertThat(response.tipo()).isEqualTo("Bearer");
        verify(jwtService).generarToken(usuarioExistente);
    }
}
