package com.caa.api.services.impl;

import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.services.EmailService;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

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
    public void enviarInvitacionOrganizacion(String emailDestino, Organizacion organizacion,
                                              RolGestion rolGestion, boolean esTerapeuta, String token) {
        String enlace = frontendUrl + "/invitaciones/aceptar?token=" + token;
        String nombreOrg = HtmlUtils.htmlEscape(organizacion.getNombre());
        String rolTexto = rolGestion == RolGestion.ADMIN ? "Administrador" : "Miembro";
        String asunto = "Te invitaron a unirte a " + nombreOrg;

        StringBuilder html = new StringBuilder();
        html.append("<p>Hola,</p>")
                .append("<p>Te invitaron a unirte a <strong>").append(nombreOrg).append("</strong> ")
                .append("con rol de gestión <strong>").append(rolTexto).append("</strong>.</p>");
        if (esTerapeuta) {
            html.append("<p>Esta membresía incluye <strong>capacidad clínica</strong>: ")
                    .append("vas a poder ser asignado como terapeuta de pacientes.</p>");
        }
        html.append("<p>Este enlace es válido por 7 días:</p>")
                .append("<p><a href=\"").append(enlace).append("\">Aceptar invitación</a></p>")
                .append("<p>Si no esperabas esta invitación, podés ignorar este email.</p>");

        enviar(emailDestino, asunto, html.toString());
    }

    @Override
    public void enviarInvitacionFamiliar(String emailDestino, Paciente paciente, PermisoColaborador permiso,
                                          String token) {
        String enlace = frontendUrl + "/invitaciones/aceptar?token=" + token;
        String nombrePaciente = HtmlUtils.htmlEscape(paciente.getNombre() + " " + paciente.getApellido());
        String permisoTexto = permiso == PermisoColaborador.LECTURA
                ? "solo lectura"
                : "edición limitada";
        String asunto = "Te invitaron a colaborar con un paciente";

        String html = "<p>Hola,</p>"
                + "<p>Te invitaron a colaborar con el paciente <strong>" + nombrePaciente + "</strong> "
                + "con permiso de <strong>" + permisoTexto + "</strong>.</p>"
                + "<p>Este enlace es válido por 7 días:</p>"
                + "<p><a href=\"" + enlace + "\">Aceptar invitación</a></p>"
                + "<p>Si no esperabas esta invitación, podés ignorar este email.</p>";

        enviar(emailDestino, asunto, html);
    }

    /**
     * Envía el email y absorbe cualquier fallo: se loguea con contexto y NO se
     * propaga la excepción. El email nunca puede tumbar la operación principal.
     */
    private void enviar(Usuario destinatario, String asunto, String html) {
        enviar(destinatario.getEmail(), asunto, html);
    }

    /**
     * Envía el email a una dirección cruda (el destino puede no tener {@link Usuario} todavía,
     * p. ej. una invitación a un email sin cuenta registrada) y absorbe cualquier fallo: se
     * loguea con contexto (asunto + destinatario, NUNCA html/link/token) y NO se propaga la
     * excepción. El email nunca puede tumbar la operación principal.
     */
    private void enviar(String destinatarioEmail, String asunto, String html) {
        try {
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(destinatarioEmail)
                    .subject(asunto)
                    .html(html)
                    .build();

            resend.emails().send(email);
        } catch (Exception e) {
            log.error("No se pudo enviar el email [{}] a {}",
                    asunto, destinatarioEmail, e);
        }
    }
}