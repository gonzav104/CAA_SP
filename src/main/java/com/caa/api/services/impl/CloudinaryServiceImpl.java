package com.caa.api.services.impl;

import com.caa.api.services.CloudinaryService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryServiceImpl.class);

    private static final List<String> MIME_PERMITIDOS = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_TAMANO_BYTES = 5L * 1024 * 1024; // 5MB

    private final Cloudinary cloudinary;

    @Override
    public String subirImagen(MultipartFile archivo, UUID pacienteId) {
        validarArchivo(archivo);

        String carpeta = "caa-sp/pacientes/" + pacienteId + "/pictogramas";
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    archivo.getBytes(),
                    ObjectUtils.asMap("folder", carpeta)
            );
            return (String) resultado.get("secure_url");

        } catch (RuntimeException | java.io.IOException ex) {
            log.error("Error al subir imagen a Cloudinary para paciente {}: {}", pacienteId, ex.getMessage(), ex);
            throw new RuntimeException("Error al procesar la imagen");
        }
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar un archivo de imagen");
        }

        String contentType = archivo.getContentType();
        if (contentType == null || !MIME_PERMITIDOS.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido. Use una imagen JPEG, PNG o WEBP");
        }

        if (archivo.getSize() > MAX_TAMANO_BYTES) {
            throw new IllegalArgumentException("La imagen supera el tamaño máximo de 5MB");
        }
    }
}