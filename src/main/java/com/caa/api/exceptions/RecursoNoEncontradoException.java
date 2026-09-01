package com.caa.api.exceptions;

/**
 * Se lanza cuando un recurso no existe o el terapeuta autenticado no tiene
 * permisos sobre él. Se mapea a HTTP 404 para no revelar si el recurso existe
 * (consistente con el criterio de no exponer información sensible).
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
