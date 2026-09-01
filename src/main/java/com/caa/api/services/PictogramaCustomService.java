package com.caa.api.services;

import com.caa.api.dtos.PictogramaCustomActualizacionDTO;
import com.caa.api.dtos.PictogramaCustomRegistroDTO;
import com.caa.api.dtos.PictogramaCustomResponseDTO;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface PictogramaCustomService {
    List<PictogramaCustomResponseDTO> obtenerPictogramasDePaciente(UUID pacienteId, String emailUsuario);

    PictogramaCustomResponseDTO obtenerPictograma(UUID pacienteId, UUID id, String emailUsuario);

    PictogramaCustomResponseDTO crearPictograma(UUID pacienteId, PictogramaCustomRegistroDTO dto,
                                                MultipartFile archivo, String emailUsuario);

    PictogramaCustomResponseDTO actualizarPictograma(UUID pacienteId, UUID id, PictogramaCustomActualizacionDTO dto,
                                                     MultipartFile archivo, String emailUsuario);

    void eliminarPictograma(UUID pacienteId, UUID id, String emailUsuario);
}