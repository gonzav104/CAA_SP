package com.caa.api.config;

import com.caa.api.exceptions.AccesoDenegadoException;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias (sin contexto Spring) de {@link GlobalExceptionHandler}.
 * Instancia el handler directamente y verifica el mapeo status/error/message
 * para las excepciones nuevas del capability "api-error-contract".
 */
@DisplayName("GlobalExceptionHandler — mapeo de excepciones a respuestas HTTP")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("AccesoDenegadoException → 403 con el mensaje de la excepcion")
    void accesoDenegado_mapeaA403ConMensajeDeLaExcepcion() {
        AccesoDenegadoException ex = new AccesoDenegadoException("Solo un terapeuta puede registrar un paciente");

        ResponseEntity<Map<String, Object>> response = handler.handleAccesoDenegado(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(403);
        assertThat(response.getBody().get("message")).isEqualTo(ex.getMessage());
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException → 400 genérico")
    void tipoDeParametroInvalido_mapeaA400Generico() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "no-es-un-uuid", java.util.UUID.class, "id", null, null);

        ResponseEntity<Map<String, Object>> response = handler.handleTipoParametroInvalido(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("message"))
                .isEqualTo("Un parametro de la solicitud tiene un formato invalido");
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 409 sin detalle de constraint/columna/SQL")
    void violacionDeIntegridad_mapeaA409SinDetalleInterno() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement; SQL [insert into usuarios ...]; "
                        + "constraint [uk_usuarios_email]");

        ResponseEntity<Map<String, Object>> response = handler.handleViolacionIntegridad(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(409);
        String mensaje = (String) response.getBody().get("message");
        assertThat(mensaje).doesNotContain("SQL", "constraint", "uk_usuarios_email", "insert into");
        assertThat(mensaje).isEqualTo("La operacion viola una restriccion de datos existente");
    }

    @Test
    @DisplayName("NoResourceFoundException → 404 genérico")
    void rutaNoEncontrada_mapeaA404Generico() {
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/v3/api-docs", "/v3/api-docs");

        ResponseEntity<Map<String, Object>> response = handler.handleRutaNoEncontrada(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(404);
        assertThat(response.getBody().get("message")).isEqualTo("La ruta solicitada no existe");
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException → 413 genérico")
    void archivoDemasiadoGrande_mapeaA413Generico() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleArchivoDemasiadoGrande(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(413);
        assertThat(response.getBody().get("message")).isEqualTo("El archivo supera el tamaño máximo permitido de 5MB");
    }

    @Test
    @DisplayName("MissingServletRequestPartException → 400 genérico")
    void parteMultipartFaltante_mapeaA400Generico() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleParteFaltante(new MissingServletRequestPartException("imagen"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("message")).isEqualTo("Falta una parte requerida de la solicitud");
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException → 415 genérico")
    void tipoDeContenidoNoSoportado_mapeaA415Generico() {
        ResponseEntity<Map<String, Object>> response = handler.handleTipoContenidoNoSoportado(
                new HttpMediaTypeNotSupportedException("Content-Type 'text/plain' is not supported"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(415);
        assertThat(response.getBody().get("message")).isEqualTo("El tipo de contenido de la solicitud no es soportado");
    }

    @Test
    @DisplayName("Límites multipart alineados con el máximo de 5MB de CloudinaryServiceImpl")
    void limitesMultipartConfigurados() throws Exception {
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(props.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("5MB");
        assertThat(props.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("6MB");
    }
}
