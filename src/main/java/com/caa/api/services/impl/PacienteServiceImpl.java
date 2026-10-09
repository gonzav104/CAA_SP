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
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
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
     * {@code POST /api/organizaciones/{organizacionId}/pacientes} (design part 1 §10.3). Dual-write
     * {@code terapeuta = creador} SIEMPRE (columna legacy todavía NOT NULL); auto-asigna
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
                .terapeuta(creador)
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

        return toResponseDTO(guardado, null);
    }

    /**
     * Unión de pacientes accesibles (design-part2 §14 item 3): 1 consulta de membresías, 1
     * consulta de pacientes (gestión + asignados clínicos, omitida por completo cuando ambos
     * conjuntos de organizaciones están vacíos), 1 consulta de vínculos familiares. El acceso de
     * equipo (gestión o clínico asignado) tiene precedencia sobre el familiar al deduplicar.
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

        Map<UUID, PacienteResponseDTO> porId = new LinkedHashMap<>();

        if (!orgIdsGestion.isEmpty() || !orgIdsClinicas.isEmpty()) {
            for (Paciente p : pacienteRepository.findAccesiblesPorEquipo(orgIdsGestion, orgIdsClinicas, usuario.getId())) {
                porId.put(p.getId(), toResponseDTO(p, null));
            }
        }

        for (PacienteFamiliar vinculo : pacienteFamiliarRepository.findByUsuario_Id(usuario.getId())) {
            Paciente p = vinculo.getPaciente();
            // El acceso de equipo tiene precedencia: si ya está presente, NO se sobrescribe
            // (quedaría con miPermiso=null, correcto para acceso de equipo).
            porId.putIfAbsent(p.getId(), toResponseDTO(p, vinculo.getPermiso()));
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

        return pacientes.stream().map(p -> toResponseDTO(p, null)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PacienteResponseDTO obtenerPaciente(UUID id, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        AccesoPaciente acceso = accesoService.exigirCapacidad(id, usuario, Capacidad.LEER);

        return toResponseDTO(acceso.paciente(), acceso.permisoFamiliar());
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

        return toResponseDTO(pacienteActualizado, null);
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

    /**
     * DEFERRADO A LA FASE 3 (desviación documentada, NO un olvido): design-part2 §11.1 dice
     * literalmente que este método debe quedar como una fachada delgada sobre
     * {@code accesoService.exigirCapacidad(LEER)}. Migrarlo AHORA, sin embargo, rompe en
     * verde-de-suite-completa a todo llamador existente fuera del alcance de esta fase
     * ({@code CartillaServiceImpl}, {@code AccesoFamiliarServiceTest}, etc. — explícitamente
     * fuera de los "Allowed edit surfaces" de la Fase 2, reservados a la Fase 3 "Cartilla +
     * nested-resource migration"), porque esos pacientes de fixture no tienen
     * {@code organizacion}/{@code Membresia} seteada todavía. Mantener la implementación
     * RolUsuario original aquí (sin cambios) preserva el contrato exacto hasta que la Fase 3
     * migre ese mismo método JUNTO con sus llamadores Cartilla/Colaborador, tal como anticipa
     * la tabla de Work Units de la Fase 3 (PR#4). Ningún test de ESTA fase depende de que este
     * método use {@code AccesoService}.
     */
    @Override
    public Paciente pacienteLegibleParaUsuario(UUID pacienteId, Usuario usuario) {
        if (usuario.getRol() == RolUsuario.TERAPEUTA) {
            return pacienteDelTerapeuta(pacienteId, usuario.getId());
        } else if (usuario.getRol() == RolUsuario.FAMILIAR) {
            return pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuario.getId())
                    .map(PacienteFamiliar::getPaciente)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Paciente no encontrado o no tiene permisos"));
        }
        throw new RecursoNoEncontradoException("Rol desconocido");
    }

    /** DEFERRADO A LA FASE 3: ver el javadoc de {@link #pacienteLegibleParaUsuario}. */
    @Override
    public void verificarEdicionParaUsuario(UUID pacienteId, Usuario usuario) {
        if (usuario.getRol() == RolUsuario.TERAPEUTA) {
            pacienteDelTerapeuta(pacienteId, usuario.getId());
        } else if (usuario.getRol() == RolUsuario.FAMILIAR) {
            PermisoColaborador permiso = pacienteFamiliarRepository
                    .findByPaciente_IdAndUsuario_Id(pacienteId, usuario.getId())
                    .map(PacienteFamiliar::getPermiso)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));
            if (permiso != PermisoColaborador.EDICION_LIMITADA) {
                throw new RecursoNoEncontradoException(
                        "Paciente no encontrado o no tiene permisos");
            }
        } else {
            throw new RecursoNoEncontradoException("Rol desconocido");
        }
    }

    private Paciente pacienteDelTerapeuta(UUID pacienteId, UUID terapeutaId) {
        return pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));
    }

    private PacienteResponseDTO toResponseDTO(Paciente p, PermisoColaborador miPermiso) {
        // organizacionId: null para pacientes legacy todavía sin backfill/organización (dual-write,
        // design §2.6); poblado en todo flujo nuevo (task 2.10).
        UUID organizacionId = p.getOrganizacion() != null ? p.getOrganizacion().getId() : null;
        return new PacienteResponseDTO(
                p.getId(),
                p.getNombre(),
                p.getApellido(),
                p.getFechaNacimiento(),
                p.getCreadoEn(),
                miPermiso,
                p.getGridSize(),
                organizacionId
        );
    }
}
