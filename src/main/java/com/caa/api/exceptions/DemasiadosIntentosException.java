package com.caa.api.exceptions;

/**
 * Se lanza cuando se supera el límite de intentos de una operación sensible
 * (login, recupero de contraseña) para un email en una ventana de tiempo.
 * Se mapea a HTTP 429 con un mensaje genérico y uniforme, sin revelar si el
 * email existe ni distinguir entre los distintos tipos de operación.
 */
public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException() {
        super("Demasiados intentos, esperá unos minutos");
    }
}
