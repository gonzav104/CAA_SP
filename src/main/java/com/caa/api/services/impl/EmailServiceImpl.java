package com.caa.api.services.impl;

import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.Usuario;
import com.caa.api.services.EmailService;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final Resend resend;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void enviarBienvenida(Usuario usuario) {
        String asunto = "¡Bienvenido a CAA SP!";
        String html = "<p>Hola <strong>" + usuario.getNombre() + "</strong>,</p>"
                + "<p>Tu cuenta en CAA SP fue creada correctamente. Ya podés empezar a usar la aplicación.</p>";

        enviar(usuario, asunto, html);
    }

    @Override
    public void enviarRecuperacionPassword(Usuario usuario, String token) {
        String enlace = frontendUrl + "/restablecer-password?token=" + token;
        String asunto = "Recupera tu contraseña";
        String html = "<p>Hola <strong>" + usuario.getNombre() + "</strong>,</p>"
                + "<p>Recibimos un pedido para restablecer tu contraseña.</p>"
                + "<p>Este enlace es válido por 1 hora:</p>"
                + "<p><a href=\"" + enlace + "\">Restablecer mi contraseña</a></p>"
                + "<p>Si no pediste este cambio, podés ignorar este email.</p>";

        enviar(usuario, asunto, html);
    }

    @Override
    public void enviarInvitacionColaborador(Usuario invitado, Paciente paciente, PermisoColaborador permiso) {
        String permisoTexto = permiso == PermisoColaborador.LECTURA
                ? "solo lectura"
                : "edición limitada";
        String asunto = "Te vincularon a un paciente";
        String html = "<p>Hola <strong>" + invitado.getNombre() + "</strong>,</p>"
                + "<p>Te vinculamos como colaborador del paciente "
                + "<strong>" + paciente.getNombre() + " " + paciente.getApellido() + "</strong>"
                + " con permiso de <strong>" + permisoTexto + "</strong>.</p>"
                + "<p>Ya podés acceder a su tablero de comunicación desde la aplicación.</p>";

        enviar(invitado, asunto, html);
    }

    /**
     * Envía el email y absorbe cualquier fallo: se loguea con contexto y NO se
     * propaga la excepción. El email nunca puede tumbar la operación principal.
     */
    private void enviar(Usuario destinatario, String asunto, String html) {
        try {
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(destinatario.getEmail())
                    .subject(asunto)
                    .html(html)
                    .build();

            resend.emails().send(email);
        } catch (Exception e) {
            log.error("No se pudo enviar el email [{}] a {}",
                    asunto, destinatario.getEmail(), e);
        }
    }
}