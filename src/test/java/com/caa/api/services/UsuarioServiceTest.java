package com.caa.api.services;

import com.caa.api.dtos.UsuarioRegistroDTO;
import com.caa.api.dtos.UsuarioResponseDTO;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.impl.UsuarioServiceImpl;
import java.time.LocalDateTime;
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
@DisplayName("UsuarioService — Tests unitarios")
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private UsuarioRegistroDTO dto;

    @BeforeEach
    void setUp() {
        dto = new UsuarioRegistroDTO("nuevo@ejemplo.com", "segura123", "Nuevo User", RolUsuario.TERAPEUTA);
    }

    @Test
    @DisplayName("Registrar con email duplicado → lanza ConflictoException (409)")
    void registrar_emailDuplicado_lanzaConflicto() {
        given(usuarioRepository.findByEmail("nuevo@ejemplo.com"))
                .willReturn(Optional.of(Usuario.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> usuarioService.registrarUsuario(dto))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya está registrado");

        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).enviarBienvenida(any());
    }

    @Test
    @DisplayName("Registrar correcto → devuelve UsuarioResponseDTO")
    void registrar_correcto_devuelveResponse() {
        given(usuarioRepository.findByEmail("nuevo@ejemplo.com"))
                .willReturn(Optional.empty());
        given(passwordEncoder.encode("segura123")).willReturn("$2a$10$hashFalso");

        Usuario guardado = Usuario.builder()
                .id(UUID.randomUUID())
                .email("nuevo@ejemplo.com")
                .passwordHash("$2a$10$hashFalso")
                .nombre("Nuevo User")
                .rol(RolUsuario.TERAPEUTA)
                .creadoEn(LocalDateTime.now())
                .build();
        given(usuarioRepository.save(any(Usuario.class))).willReturn(guardado);

        UsuarioResponseDTO response = usuarioService.registrarUsuario(dto);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("nuevo@ejemplo.com");
        assertThat(response.rol()).isEqualTo(RolUsuario.TERAPEUTA);
        verify(emailService).enviarBienvenida(guardado);
    }
}
