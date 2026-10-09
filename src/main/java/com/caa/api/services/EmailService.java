package com.caa.api.services;

import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;

/**
 * Envío de emails transaccionales vía Resend.
 * <p>
 * CRÍTICO: el envío de email es un efecto secundario — NUNCA debe hacer fallar
 * la operación principal (registro, invitación, pedido de recupero). Ninguno
 * de los métodos propaga excepciones: si Resend falla, se loguea el error y
 * el método retorna normalmente.
 */
public interface EmailService {

    void enviarBienvenida(Usuario usuario);

    void enviarRecuperacionPassword(Usuario usuario, String token);

    /**
     * Invitación a una organización (design §9.5). El email destino puede no tener cuenta
     * todavía — por eso recibe un {@code String}, no un {@link Usuario}. {@code rolGestion} y
     * {@code esTerapeuta} se describen como DOS atributos independientes, nunca como un único
     * "rol".
     */
    void enviarInvitacionOrganizacion(String emailDestino, Organizacion organizacion,
                                       RolGestion rolGestion, boolean esTerapeuta, String token);

    /**
     * Invitación familiar a un paciente específico (design §9.5). El email destino puede no
     * tener cuenta todavía.
     */
    void enviarInvitacionFamiliar(String emailDestino, Paciente paciente, PermisoColaborador permiso,
                                   String token);
}