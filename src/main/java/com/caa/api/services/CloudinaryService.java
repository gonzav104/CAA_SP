package com.caa.api.services;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {
    /**
     * Sube una imagen a Cloudinary en la carpeta del paciente y devuelve su URL pública.
     *
     * @param archivo    imagen a subir (jpeg, png o webp, máx 5MB)
     * @param pacienteId id del paciente dueño de la imagen
     * @return URL pública de la imagen subida
     * @throws IllegalArgumentException si el archivo no es una imagen válida o excede el tamaño máximo
     */
    String subirImagen(MultipartFile archivo, UUID pacienteId);
}