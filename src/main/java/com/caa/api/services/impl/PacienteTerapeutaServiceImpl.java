package com.caa.api.services.impl;

import com.caa.api.dtos.AsignacionTerapeutaDTO;
import com.caa.api.dtos.TerapeutaAsignadoResponseDTO;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteTerapeuta;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.PacienteTerapeutaService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de asignaciones clínicas {@code PacienteTerapeuta} (design-part2 §12.2, §13; tareas
 * 5.14-5.16). Listar exige {@code Capacidad.GESTION_CLINICA} (equivale a {@code acceso.esEquipo()}
 * en la matriz de {@code AccesoServiceImpl}); asignar/desasignar exigen
 * {@code Capacidad.ADMINISTRAR} (rolGestion OWNER/ADMIN), independiente del propio
 * {@code esTerapeuta} del llamante.
 * <p>
 * Orden del bloqueo (distinto del de {@code OrganizacionServiceImpl}, deliberadamente): aquí el
 * {@code organizacionId} no se conoce hasta resolver el paciente, así que la verificación de
 * {@code ADMINISTRAR} del llamante (que resuelve el paciente) ocurre antes de adquirir el bloqueo.
 * La carrera real que importa — el precondicionante {@code esTerapeuta} del OBJETIVO frente a un
 * toggle/baja concurrente — queda protegida: la membresía del objetivo se lee y la fila
 * {@code PacienteTerapeuta} se inserta/borra solo DESPUÉS de adquirir
 * {@code OrganizacionRepository.findConBloqueoById} (design §3.3 punto 3, ya anticipado en su
 * javadoc para "asignación de terapeuta").
 */
@Service
@RequiredArgsConstructor
public class PacienteTerapeutaServiceImpl implements PacienteTerapeutaService {

    private final UsuarioRepository usuarioRepository;
    private final MembresiaRepository membresiaRepository;
    private final PacienteTerapeutaRepository pacienteTerapeutaRepository;
    private final OrganizacionRepository organizacionRepository;
    private final AccesoService accesoService;

    @Override
    @Transactional(readOnly = true)
    public List<TerapeutaAsignadoResponseDTO> listarAsignados(UUID pacienteId, String email) {
        Usuario usuario = usuarioAutenticado(email);
        accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA);

        return pacienteTerapeutaRepository.findByPaciente_Id(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public TerapeutaAsignadoResponseDTO asignar(UUID pacienteId, AsignacionTerapeutaDTO dto, String email) {
        Usuario caller = usuarioAutenticado(email);
        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR);
        Paciente paciente = acceso.paciente();
        UUID organizacionId = paciente.getOrganizacion().getId();

        bloquearOrganizacion(organizacionId);

        Membresia objetivoMembresia = membresiaRepository
                .findById(new MembresiaId(organizacionId, dto.usuarioId()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Paciente no encontrado o no tiene permisos"));

        if (!objetivoMembresia.isEsTerapeuta()) {
            throw new ConflictoException("El miembro no tiene capacidad clínica");
        }

        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, dto.usuarioId());
        PacienteTerapeuta asignacion = pacienteTerapeutaRepository.findById(id)
                .orElseGet(() -> pacienteTerapeutaRepository.saveAndFlush(PacienteTerapeuta.builder()
                        .id(id)
                        .paciente(paciente)
                        .usuario(objetivoMembresia.getUsuario())
                        .organizacionId(organizacionId)
                        .build()));

        return toResponseDTO(asignacion);
    }

    @Override
    @Transactional
    public void desasignar(UUID pacienteId, UUID usuarioId, String email) {
        Usuario caller = usuarioAutenticado(email);
        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR);
        UUID organizacionId = acceso.paciente().getOrganizacion().getId();

        bloquearOrganizacion(organizacionId);

        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, usuarioId);
        if (pacienteTerapeutaRepository.existsById(id)) {
            pacienteTerapeutaRepository.deleteById(id);
        }
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Usuario usuarioAutenticado(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    /**
     * Mismo patrón que {@code OrganizacionServiceImpl.bloquearOrganizacion}: adquiere el bloqueo
     * exclusivo de fila sobre la organización ANTES de leer/escribir Membresia o PacienteTerapeuta.
     */
    private void bloquearOrganizacion(UUID organizacionId) {
        organizacionRepository.findConBloqueoById(organizacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Organización no encontrada o no tiene permisos"));
    }

    private TerapeutaAsignadoResponseDTO toResponseDTO(PacienteTerapeuta pt) {
        Usuario usuario = pt.getUsuario();
        return new TerapeutaAsignadoResponseDTO(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), pt.getAsignadoEn());
    }
}
