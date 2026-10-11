package com.caa.api.services;

import com.caa.api.dtos.PacienteActualizacionDTO;
import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.models.Paciente;
import com.caa.api.models.Usuario;
import java.util.List;
import java.util.UUID;

public interface PacienteService {

    /**
     * {@code POST /api/organizaciones/{organizacionId}/pacientes} (design part 1 §10.3): exige
     * {@code AccesoService.exigirAltaPaciente}; auto-asigna {@code PacienteTerapeuta(creador)}
     * en la misma transacción solo si
     * {@code esTerapeuta = true}.
     */
    PacienteResponseDTO registrarPaciente(UUID organizacionId, PacienteRegistroDTO dto, String email);

    List<PacienteResponseDTO> obtenerMisPacientes(String emailTerapeuta);

    /**
     * {@code GET /api/organizaciones/{organizacionId}/pacientes}: rolGestion OWNER/ADMIN ve todo
     * el workspace; MIEMBRO con esTerapeuta=true ve solo lo asignado; MIEMBRO con
     * esTerapeuta=false ve una lista vacía sin consultar pacientes (design-part2 §14).
     */
    List<PacienteResponseDTO> obtenerPacientesDeOrganizacion(UUID organizacionId, String email);
    PacienteResponseDTO obtenerPaciente(UUID id, String email);
    PacienteResponseDTO actualizarPaciente(UUID id, PacienteActualizacionDTO dto, String emailTerapeuta);
    void eliminarPaciente(UUID id, String emailTerapeuta);
    Paciente pacienteLegibleParaUsuario(UUID pacienteId, Usuario usuario);
    void verificarEdicionParaUsuario(UUID pacienteId, Usuario usuario);
}
