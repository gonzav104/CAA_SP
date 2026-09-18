package com.caa.api.exceptions;

/**
 * Se lanza cuando el usuario autenticado no tiene el rol requerido para la
 * operacion solicitada. Se mapea a HTTP 403 Forbidden.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
