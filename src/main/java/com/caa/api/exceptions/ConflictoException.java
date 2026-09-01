package com.caa.api.exceptions;

/**
 * Se lanza cuando la operacion entra en conflicto con el estado actual del
 * recurso (por ejemplo, intentar crear una entidad con una clave unica ya
 * existente). Se mapea a HTTP 409 Conflict.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
