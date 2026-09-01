package com.caa.api.services;

import com.caa.api.dtos.PacienteActualizacionDTO;
import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.models.Paciente;
import com.caa.api.models.Usuario;
import java.util.List;
import java.util.UUID;

public interface PacienteService {
    PacienteResponseDTO registrarPaciente(PacienteRegistroDTO dto, String emailTerapeuta);
    List<PacienteResponseDTO> obtenerMisPacientes(String emailTerapeuta);
    PacienteResponseDTO actualizarPaciente(UUID id, PacienteActualizacionDTO dto, String emailTerapeuta);
    void eliminarPaciente(UUID id, String emailTerapeuta);
    Paciente pacienteLegibleParaUsuario(UUID pacienteId, Usuario usuario);
    void verificarEdicionParaUsuario(UUID pacienteId, Usuario usuario);
}
