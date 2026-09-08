package com.caa.api.services;

import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.Usuario;

/**
 * Envío de emails transaccionales vía Resend.
 * <p>
 * CRÍTICO: el envío de email es un efecto secundario — NUNCA debe hacer fallar
 * la operación principal (registro, vinculación de colaborador, pedido de
 * recupero). Ninguno de los métodos propaga excepciones: si Resend falla, se
 * loguea el error y el método retorna normalmente.
 */
public interface EmailService {

    void enviarBienvenida(Usuario usuario);

    void enviarRecuperacionPassword(Usuario usuario, String token);

    void enviarInvitacionColaborador(Usuario invitado, Paciente paciente, PermisoColaborador permiso);
}