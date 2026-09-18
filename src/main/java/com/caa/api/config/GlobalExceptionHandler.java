package com.caa.api.config;

import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.exceptions.DemasiadosIntentosException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, Object>> handleCredencialesInvalidas(
            CredencialesInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 401,
                        "error", "Credenciales invalidas",
                        "message", "Email o password incorrectos"
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Datos de entrada invalidos");

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 400,
                        "error", "Validacion fallida",
                        "message", mensaje
                ));
    }

    /**
     * 400 genérico para body JSON malformado o no interpretable (incluye valores de
     * enum no reconocidos, por ejemplo un "paradigma" fuera del vocabulario soportado).
     * Sin este handler, Spring delega en el catch-all genérico y responde 500,
     * lo cual es incorrecto: un body inválido siempre es error del cliente.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMensajeNoLegible(
            HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 400,
                        "error", "Solicitud invalida",
                        "message", "El cuerpo de la solicitud es invalido o contiene valores no soportados"
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 400,
                        "error", "Solicitud invalida",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleRecursoNoEncontrado(
            RecursoNoEncontradoException ex) {
        log.warn("RecursoNoEncontradoException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 404,
                        "error", "Recurso no encontrado",
                        "message", ex.getMessage()
                ));
    }

    /**
     * 429 genérico y uniforme para el rate limit (login, recupero de password).
     * El mensaje es genérico: no revela qué operación se limitó ni si el email existe.
     */
    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<Map<String, Object>> handleDemasiadosIntentos(
            DemasiadosIntentosException ex) {
        log.warn("Rate limit alcanzado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 429,
                        "error", "Demasiadas peticiones",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<Map<String, Object>> handleConflicto(
            ConflictoException ex) {
        log.warn("ConflictoException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 409,
                        "error", "Conflicto",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<Map<String, Object>> handleAccesoDenegado(
            AccesoDenegadoException ex) {
        log.warn("AccesoDenegadoException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 403,
                        "error", "Acceso denegado",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMetodoNoSoportado(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Metodo HTTP no soportado: {}", ex.getMessage());
        String metodosPermitidos = ex.getSupportedHttpMethods() == null
                ? ""
                : ex.getSupportedHttpMethods().stream()
                        .map(m -> m.name())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
        String mensaje = metodosPermitidos.isBlank()
                ? "Método HTTP no soportado para esta ruta"
                : "Método HTTP no soportado para esta ruta. Métodos permitidos: " + metodosPermitidos;

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 405,
                        "error", "Método no permitido",
                        "message", mensaje
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoParametroInvalido(
            MethodArgumentTypeMismatchException ex) {
        log.warn("MethodArgumentTypeMismatchException: parametro={}, mensaje={}",
                ex.getName(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 400,
                        "error", "Solicitud invalida",
                        "message", "Un parametro de la solicitud tiene un formato invalido"
                ));
    }

    /**
     * 409 genérico para violaciones de restricciones de integridad de datos (por
     * ejemplo, una clave única duplicada detectada recién al confirmar el write).
     * El mensaje al cliente nunca incluye nombre de constraint, columna, tabla ni
     * fragmento SQL; la excepción completa se registra solo en el servidor.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleViolacionIntegridad(
            DataIntegrityViolationException ex) {
        log.error("DataIntegrityViolationException", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 409,
                        "error", "Conflicto",
                        "message", "La operacion viola una restriccion de datos existente"
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRutaNoEncontrada(
            NoResourceFoundException ex) {
        log.warn("NoResourceFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 404,
                        "error", "Recurso no encontrado",
                        "message", "La ruta solicitada no existe"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Excepcion no manejada", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 500,
                        "error", "Error interno",
                        "message", "Ocurrio un error inesperado"
                ));
    }
}
