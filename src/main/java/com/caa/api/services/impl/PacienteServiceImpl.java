package com.caa.api.services.impl;

import com.caa.api.dtos.PacienteActualizacionDTO;
import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteTerapeuta;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.PacienteService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PacienteServiceImpl implements PacienteService {

    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PacienteFamiliarRepository pacienteFamiliarRepository;
    private final AccesoService accesoService;
    private final PacienteTerapeutaRepository pacienteTerapeutaRepository;
    private final MembresiaRepository membresiaRepository;

    /**
     * {@code POST /api/organizaciones/{organizacionId}/pacientes} (design part 1 §10.3). Auto-asigna
     * {@code PacienteTerapeuta(creador)} en la MISMA transacción solo si {@code esTerapeuta = true}.
     * Un creador OWNER/ADMIN con esTerapeuta=false crea el paciente SIN asignar (visible de todas
     * formas vía acceso organization-wide).
     */
    @Override
    @Transactional
    public PacienteResponseDTO registrarPaciente(UUID organizacionId, PacienteRegistroDTO dto, String email) {
        Usuario creador = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Membresia membresia = accesoService.exigirAltaPaciente(organizacionId, creador);

        Paciente paciente = Paciente.builder()
                .nombre(dto.nombre())
                .apellido(dto.apellido())
                .fechaNacimiento(dto.fechaNacimiento())
                .organizacion(membresia.getOrganizacion())
                .build();

        Paciente guardado = pacienteRepository.save(paciente);

        if (membresia.isEsTerapeuta()) {
            PacienteTerapeuta asignacion = PacienteTerapeuta.builder()
                    .id(new PacienteTerapeutaId(guardado.getId(), creador.getId()))
                    .paciente(guardado)
                    .usuario(creador)
                    .organizacionId(organizacionId)
                    .build();
            pacienteTerapeutaRepository.save(asignacion);
        }

        // El creador queda asignado exactamente cuando esTerapeuta = true (fila guardada arriba).
        return toResponseDTO(new AccesoPaciente(guardado, membresia, membresia.isEsTerapeuta(), null));
    }

    /**
     * Unión de pacientes accesibles (design-part2 §14 item 3): 1 consulta de membresías, 1
     * consulta de pacientes (gestión + asignados clínicos, omitida por completo cuando ambos
     * conjuntos de organizaciones están vacíos), 1 consulta de vínculos familiares. El acceso de
     * equipo (gestión o clínico asignado) tiene precedencia sobre el familiar al deduplicar.
     * Las capacidades se derivan de las membresías ya cargadas, sin consultas adicionales.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PacienteResponseDTO> obtenerMisPacientes(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        List<Membresia> membresias = membresiaRepository.findByUsuario_Id(usuario.getId());
        Set<UUID> orgIdsGestion = membresias.stream()
                .filter(Membresia::esGestion)
                .map(m -> m.getOrganizacion().getId())
                .collect(Collectors.toSet());
        Set<UUID> orgIdsClinicas = membresias.stream()
                .filter(m -> !m.esGestion() && m.isEsTerapeuta())
                .map(m -> m.getOrganizacion().getId())
                .collect(Collectors.toSet());

        Map<UUID, Membresia> membresiaPorOrg = membresias.stream()
                .collect(Collectors.toMap(m -> m.getOrganizacion().getId(), Function.identity()));

        Map<UUID, PacienteResponseDTO> porId = new LinkedHashMap<>();

        if (!orgIdsGestion.isEmpty() || !orgIdsClinicas.isEmpty()) {
            for (Paciente p : pacienteRepository.findAccesiblesPorEquipo(orgIdsGestion, orgIdsClinicas, usuario.getId())) {
                Membresia membresia = membresiaPorOrg.get(p.getOrganizacion().getId());
                // Fuera de gestión, la consulta solo devuelve pacientes asignados clínicamente.
                porId.put(p.getId(), toResponseDTO(new AccesoPaciente(p, membresia, !membresia.esGestion(), null)));
            }
        }

        for (PacienteFamiliar vinculo : pacienteFamiliarRepository.findByUsuario_Id(usuario.getId())) {
            Paciente p = vinculo.getPaciente();
            // El acceso de equipo tiene precedencia: si ya está presente, NO se sobrescribe
            // (quedaría con miPermiso=null, correcto para acceso de equipo).
            porId.putIfAbsent(p.getId(), toResponseDTO(new AccesoPaciente(
                    p, membresiaPorOrg.get(p.getOrganizacion().getId()), false, vinculo.getPermiso())));
        }

        return List.copyOf(porId.values());
    }

    /**
     * {@code GET /api/organizaciones/{organizacionId}/pacientes} (design-part2 §14 item 3):
     * rolGestion OWNER/ADMIN ve el workspace completo (1 consulta); MIEMBRO con esTerapeuta=true
     * ve solo lo asignado (1 consulta); MIEMBRO con esTerapeuta=false ve una lista vacía SIN
     * consultar pacientes (invariante design §3.5: no puede existir una fila PacienteTerapeuta
     * para él).
     */
    @Override
    @Transactional(readOnly = true)
    public List<PacienteResponseDTO> obtenerPacientesDeOrganizacion(UUID organizacionId, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Membresia membresia = accesoService.exigirMembresia(organizacionId, usuario);

        List<Paciente> pacientes;
        if (membresia.esGestion()) {
            pacientes = pacienteRepository.findByOrganizacion_Id(organizacionId);
        } else if (membresia.isEsTerapeuta()) {
            pacientes = pacienteRepository.findAsignadosEnOrganizacion(organizacionId, usuario.getId());
        } else {
            return List.of();
        }

        boolean asignado = !membresia.esGestion();
        return pacientes.stream()
                .map(p -> toResponseDTO(new AccesoPaciente(p, membresia, asignado, null)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PacienteResponseDTO obtenerPaciente(UUID id, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        AccesoPaciente acceso = accesoService.exigirCapacidad(id, usuario, Capacidad.LEER);

        return toResponseDTO(acceso);
    }

    @Override
    @Transactional
    public PacienteResponseDTO actualizarPaciente(UUID id, PacienteActualizacionDTO dto, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        AccesoPaciente acceso = accesoService.exigirCapacidad(id, usuario, Capacidad.EDITAR_CONTENIDO);
        Paciente paciente = acceso.paciente();

        paciente.setNombre(dto.nombre());
        paciente.setApellido(dto.apellido());
        paciente.setFechaNacimiento(dto.fechaNacimiento());
        if (dto.gridSize() != null) {
            paciente.setGridSize(dto.gridSize());
        }

        Paciente pacienteActualizado = pacienteRepository.save(paciente);

        return toResponseDTO(new AccesoPaciente(
                pacienteActualizado, acceso.membresia(), acceso.asignado(), acceso.permisoFamiliar()));
    }

    /** Baja de paciente: ADMINISTRAR (rolGestion OWNER/ADMIN), independiente de esTerapeuta (design R3). */
    @Override
    @Transactional
    public void eliminarPaciente(UUID id, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        AccesoPaciente acceso = accesoService.exigirCapacidad(id, usuario, Capacidad.ADMINISTRAR);

        pacienteRepository.delete(acceso.paciente());
    }

    /** {@code miPermiso} es null salvo acceso efectivo familiar: AccesoPaciente nunca lo combina con equipo. */
    private PacienteResponseDTO toResponseDTO(AccesoPaciente acceso) {
        Paciente p = acceso.paciente();
        UUID organizacionId = p.getOrganizacion().getId();
        return new PacienteResponseDTO(
                p.getId(),
                p.getNombre(),
                p.getApellido(),
                p.getFechaNacimiento(),
                p.getCreadoEn(),
                acceso.permisoFamiliar(),
                p.getGridSize(),
                organizacionId,
                acceso.capacidadesEfectivas()
        );
    }
}
