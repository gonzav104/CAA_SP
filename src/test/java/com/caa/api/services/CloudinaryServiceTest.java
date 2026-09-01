package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.caa.api.services.impl.CloudinaryServiceImpl;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryService — validaciones y subida de imágenes")
class CloudinaryServiceTest {

    private static final String URL_SUBIDA = "https://res.cloudinary.com/test/image/upload/v1/caa-sp/pacientes/abc/pictogramas/foto.png";

    private Uploader uploader;
    private Cloudinary cloudinary;
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        cloudinaryService = new CloudinaryServiceImpl(cloudinary);
    }

    @Test
    @DisplayName("Archivo de imagen válido → devuelve la URL pública del upload")
    void archivoValido_devuelveUrl() throws Exception {
        MockMultipartFile imagen = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3, 4});
        UUID pacienteId = UUID.randomUUID();

        given(cloudinary.uploader()).willReturn(uploader);
        given(uploader.upload(any(byte[].class), anyMap())).willReturn(Map.of("secure_url", URL_SUBIDA));

        String url = cloudinaryService.subirImagen(imagen, pacienteId);

        assertThat(url).isEqualTo(URL_SUBIDA);
    }

    @Test
    @DisplayName("Archivo con tipo no permitido (text/plain) → IllegalArgumentException")
    void tipoNoPermitido_lanzaIllegalArgument() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "notas.txt", "text/plain", "hola".getBytes());

        assertThatThrownBy(() -> cloudinaryService.subirImagen(archivo, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de archivo no permitido");
    }

    @Test
    @DisplayName("Archivo que supera 5MB → IllegalArgumentException")
    void archivoMuyGrande_lanzaIllegalArgument() {
        byte[] pesado = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "grande.jpg", "image/jpeg", pesado);

        assertThatThrownBy(() -> cloudinaryService.subirImagen(archivo, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamaño máximo de 5MB");
    }

    @Test
    @DisplayName("Error de Cloudinary (network/API) → RuntimeException genérica sin exponer detalles")
    void errorCloudinary_lanzaRuntimeExceptionGenerica() throws Exception {
        MockMultipartFile imagen = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3});

        given(cloudinary.uploader()).willReturn(uploader);
        given(uploader.upload(any(byte[].class), anyMap()))
                .willThrow(new RuntimeException("boom: invalid api key 12345"));

        assertThatThrownBy(() -> cloudinaryService.subirImagen(imagen, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error al procesar la imagen")
                .hasMessageNotContaining("api key")
                .hasMessageNotContaining("12345");
    }

    @Test
    @DisplayName("Archivo vacío → IllegalArgumentException")
    void archivoVacio_lanzaIllegalArgument() {
        MockMultipartFile vacio = new MockMultipartFile(
                "archivo", "vacio.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> cloudinaryService.subirImagen(vacio, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debe enviar un archivo");
    }
}