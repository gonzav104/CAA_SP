package com.caa.api.exceptions;

public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales invalidas");
    }

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
