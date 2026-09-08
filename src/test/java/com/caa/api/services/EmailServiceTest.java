package com.caa.api.services;

import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.services.impl.EmailServiceImpl;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService — Tests unitarios (el envío nunca debe propagar errores)")
class EmailServiceTest {

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    @InjectMocks
    private EmailServiceImpl emailService;

    private Usuario usuario;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .email("mama@ejemplo.com")
                .nombre("Mama de Nico")
                .rol(RolUsuario.FAMILIAR)
                .build();
        paciente = Paciente.builder()
                .id(UUID.randomUUID())
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build();

        // Los @Value no se inyectan con Mockito: se setean explícitamente.
        ReflectionTestUtils.setField(emailService, "fromEmail", "onboarding@resend.dev");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5173");

        given(resend.emails()).willReturn(emails);
    }

    // ──────────────────────────────────────────────
    //  SAD PATHS — el fallo de envío NO se propaga
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("enviarBienvenida con envío fallido → no lanza excepción")
    void enviarBienvenida_envioFallido_noPropaga() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class)))
                .willThrow(new ResendException("API de Resend caída"));

        assertThatCode(() -> emailService.enviarBienvenida(usuario))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enviarRecuperacionPassword con envío fallido → no lanza excepción")
    void enviarRecuperacionPassword_envioFallido_noPropaga() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class)))
                .willThrow(new ResendException("API de Resend caída"));

        assertThatCode(() -> emailService.enviarRecuperacionPassword(usuario, "token-123"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enviarInvitacionColaborador con envío fallido → no lanza excepción")
    void enviarInvitacionColaborador_envioFallido_noPropaga() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class)))
                .willThrow(new ResendException("API de Resend caída"));

        assertThatCode(() -> emailService.enviarInvitacionColaborador(
                usuario, paciente, PermisoColaborador.LECTURA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Fallo inesperado (RuntimeException) en el envío → tampoco propaga")
    void envio_conErrorInesperado_noPropaga() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class)))
                .willThrow(new IllegalStateException("error de red inesperado"));

        assertThatCode(() -> emailService.enviarBienvenida(usuario))
                .doesNotThrowAnyException();
    }

    // ──────────────────────────────────────────────
    //  HAPPY PATHS — el email se arma con lo esperado
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("enviarBienvenida → email al destinatario con asunto de bienvenida")
    void enviarBienvenida_armaEmailCorrecto() throws ResendException {
        emailService.enviarBienvenida(usuario);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        CreateEmailOptions email = captor.getValue();
        assertThat(email.getFrom()).isEqualTo("onboarding@resend.dev");
        assertThat(email.getTo()).containsExactly("mama@ejemplo.com");
        assertThat(email.getSubject()).contains("Bienvenido");
        assertThat(email.getHtml()).contains("Mama de Nico");
    }

    @Test
    @DisplayName("enviarRecuperacionPassword → incluye el enlace con el token en el cuerpo")
    void enviarRecuperacionPassword_incluyeEnlaceConToken() throws ResendException {
        emailService.enviarRecuperacionPassword(usuario, "token-abc-123");

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        CreateEmailOptions email = captor.getValue();
        assertThat(email.getTo()).containsExactly("mama@ejemplo.com");
        assertThat(email.getHtml())
                .contains("http://localhost:5173/restablecer-password?token=token-abc-123");
    }

    @Test
    @DisplayName("enviarInvitacionColaborador → menciona al paciente y el permiso")
    void enviarInvitacionColaborador_mencionaPacienteYPermiso() throws ResendException {
        emailService.enviarInvitacionColaborador(usuario, paciente, PermisoColaborador.EDICION_LIMITADA);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        CreateEmailOptions email = captor.getValue();
        assertThat(email.getTo()).containsExactly("mama@ejemplo.com");
        assertThat(email.getHtml())
                .contains("Nico Perez")
                .contains("edición limitada");
    }
}