package com.caa.api.services.impl;

import com.caa.api.dtos.MiembroResponseDTO;
import com.caa.api.dtos.MiembroRolGestionDTO;
import com.caa.api.dtos.MiembroTerapeutaDTO;
import com.caa.api.dtos.OrganizacionActualizacionDTO;
import com.caa.api.dtos.OrganizacionRegistroDTO;
import com.caa.api.dtos.OrganizacionResponseDTO;
import com.caa.api.dtos.TransferenciaPropiedadDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.OrganizacionService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de {@code Organizacion} y gestión de {@code Membresia} (design §12.2, design-part2 §13).
 * Toda mutación de membresías adquiere primero {@code OrganizacionRepository.findConBloqueoById}
 * (design §3.3 punto 3) antes de leer o escribir cualquier {@code Membresia}.
 */
@Service
@RequiredArgsConstructor
public class OrganizacionServiceImpl implements OrganizacionService {

    private final OrganizacionRepository organizacionRepository;
    private final MembresiaRepository membresiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final PacienteTerapeutaRepository pacienteTerapeutaRepository;
    private final AccesoService accesoService;

    @Override
    @Transactional
    public OrganizacionResponseDTO crear(OrganizacionRegistroDTO dto, String email) {
        Usuario creador = usuarioAutenticado(email);
        boolean esTerapeuta = dto.esTerapeuta() == null || dto.esTerapeuta();

        Organizacion organizacion = Organizacion.builder()
                .nombre(dto.nombre())
                .creadoPor(creador)
                .build();
        Organizacion guardada = organizacionRepository.save(organizacion);

        Membresia membresia = Membresia.builder()
                .id(new MembresiaId(guardada.getId(), creador.getId()))
                .organizacion(guardada)
                .usuario(creador)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(esTerapeuta)
                .build();
        membresiaRepository.save(membresia);

        return new OrganizacionResponseDTO(guardada.getId(), guardada.getNombre(), RolGestion.OWNER, esTerapeuta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizacionResponseDTO> listarMisOrganizaciones(String email) {
        Usuario usuario = usuarioAutenticado(email);
        return membresiaRepository.findByUsuario_Id(usuario.getId()).stream()
                .map(this::toOrganizacionDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizacionResponseDTO obtener(UUID organizacionId, String email) {
        Usuario usuario = usuarioAutenticado(email);
        Membresia membresia = accesoService.exigirMembresia(organizacionId, usuario);
        return toOrganizacionDTO(membresia);
    }

    @Override
    @Transactional
    public OrganizacionResponseDTO renombrar(UUID organizacionId, OrganizacionActualizacionDTO dto, String email) {
        Usuario usuario = usuarioAutenticado(email);
        Membresia membresia = accesoService.exigirRolGestion(
                organizacionId, usuario, Set.of(RolGestion.OWNER, RolGestion.ADMIN));

        Organizacion organizacion = membresia.getOrganizacion();
        organizacion.setNombre(dto.nombre());
        Organizacion guardada = organizacionRepository.save(organizacion);

        return new OrganizacionResponseDTO(
                guardada.getId(), guardada.getNombre(), membresia.getRolGestion(), membresia.isEsTerapeuta());
    }

    /**
     * El bloqueo de fila no es necesario aquí (design §12.2 no lo menciona para este endpoint):
     * la FK {@code pacientes.organizacion_id -> organizaciones ON DELETE RESTRICT} (design §3.1)
     * ya actúa como red de seguridad ante una carrera con una alta de paciente concurrente —
     * el DELETE fallaría con {@code DataIntegrityViolationException} (409) en vez de perder datos.
     */
    @Override
    @Transactional
    public void eliminar(UUID organizacionId, String email) {
        Usuario usuario = usuarioAutenticado(email);
        accesoService.exigirRolGestion(organizacionId, usuario, Set.of(RolGestion.OWNER));

        if (pacienteRepository.existsByOrganizacion_Id(organizacionId)) {
            throw new ConflictoException(
                    "No se puede eliminar la organización mientras tenga pacientes registrados");
        }

        organizacionRepository.deleteById(organizacionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MiembroResponseDTO> listarMiembros(UUID organizacionId, String email) {
        Usuario usuario = usuarioAutenticado(email);
        accesoService.exigirMembresia(organizacionId, usuario);

        return membresiaRepository.findByOrganizacion_Id(organizacionId).stream()
                .map(this::toMiembroDTO)
                .toList();
    }

    @Override
    @Transactional
    public MiembroResponseDTO cambiarRolGestion(UUID organizacionId, UUID usuarioId, MiembroRolGestionDTO dto,
                                                 String email) {
        if (dto.rolGestion() == RolGestion.OWNER) {
            throw new IllegalArgumentException("No se puede asignar el rol OWNER mediante este endpoint");
        }

        Usuario caller = usuarioAutenticado(email);
        bloquearOrganizacion(organizacionId);
        accesoService.exigirRolGestion(organizacionId, caller, Set.of(RolGestion.OWNER));

        Membresia objetivo = membresiaEnOrganizacion(organizacionId, usuarioId);
        if (objetivo.getRolGestion() == RolGestion.OWNER) {
            throw new AccesoDenegadoException(
                    "No se puede modificar el rol de gestión del OWNER mediante este endpoint");
        }

        objetivo.setRolGestion(dto.rolGestion());
        return toMiembroDTO(membresiaRepository.save(objetivo));
    }

    @Override
    @Transactional
    public MiembroResponseDTO cambiarEsTerapeuta(UUID organizacionId, UUID usuarioId, MiembroTerapeutaDTO dto,
                                                  String email) {
        Usuario caller = usuarioAutenticado(email);
        bloquearOrganizacion(organizacionId);
        Membresia callerMembresia = accesoService.exigirRolGestion(
                organizacionId, caller, Set.of(RolGestion.OWNER, RolGestion.ADMIN));

        Membresia objetivo = membresiaEnOrganizacion(organizacionId, usuarioId);
        if (callerMembresia.getRolGestion() == RolGestion.ADMIN && objetivo.getRolGestion() == RolGestion.OWNER) {
            throw new AccesoDenegadoException("ADMIN no puede modificar la capacidad clínica del OWNER");
        }

        boolean nuevoValor = dto.esTerapeuta();
        objetivo.setEsTerapeuta(nuevoValor);
        Membresia guardado = membresiaRepository.save(objetivo);

        if (!nuevoValor) {
            pacienteTerapeutaRepository.deleteByOrganizacionIdAndUsuario_Id(organizacionId, usuarioId);
        }

        return toMiembroDTO(guardado);
    }

    @Override
    @Transactional
    public void eliminarMiembro(UUID organizacionId, UUID usuarioId, String email) {
        Usuario caller = usuarioAutenticado(email);
        bloquearOrganizacion(organizacionId);
        Membresia callerMembresia = accesoService.exigirMembresia(organizacionId, caller);

        Membresia objetivo = membresiaEnOrganizacion(organizacionId, usuarioId);

        if (objetivo.getRolGestion() == RolGestion.OWNER) {
            throw new ConflictoException(
                    "El OWNER debe transferir la propiedad antes de salir o ser eliminado de la organización");
        }

        boolean esSalidaVoluntaria = caller.getId().equals(usuarioId);
        if (!esSalidaVoluntaria) {
            RolGestion callerRol = callerMembresia.getRolGestion();
            if (callerRol == RolGestion.ADMIN) {
                if (objetivo.getRolGestion() != RolGestion.MIEMBRO) {
                    throw new AccesoDenegadoException("ADMIN solo puede eliminar miembros con rol MIEMBRO");
                }
            } else if (callerRol != RolGestion.OWNER) {
                throw new AccesoDenegadoException("No tiene permisos para eliminar miembros de esta organización");
            }
        }

        pacienteTerapeutaRepository.deleteByOrganizacionIdAndUsuario_Id(organizacionId, usuarioId);
        membresiaRepository.delete(objetivo);
    }

    @Override
    @Transactional
    public void transferirPropiedad(UUID organizacionId, TransferenciaPropiedadDTO dto, String email) {
        Usuario caller = usuarioAutenticado(email);
        bloquearOrganizacion(organizacionId);
        Membresia ownerActual = accesoService.exigirRolGestion(organizacionId, caller, Set.of(RolGestion.OWNER));

        UUID nuevoOwnerId = dto.nuevoOwnerUsuarioId();
        if (nuevoOwnerId.equals(caller.getId())) {
            throw new ConflictoException("El nuevo propietario debe ser un miembro distinto del actual");
        }

        Membresia nuevoOwner = membresiaEnOrganizacion(organizacionId, nuevoOwnerId);

        // Orden obligatorio (design §3.3 punto 2): el índice único es inmediato, así que degradar
        // primero evita tener momentáneamente DOS filas rolGestion=OWNER dentro de esta transacción.
        ownerActual.setRolGestion(RolGestion.ADMIN);
        membresiaRepository.save(ownerActual);

        nuevoOwner.setRolGestion(RolGestion.OWNER);
        membresiaRepository.save(nuevoOwner);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Usuario usuarioAutenticado(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    /**
     * Adquiere el bloqueo exclusivo de fila sobre la organización (design §3.3 punto 3), primero,
     * antes de cualquier lectura/escritura de Membresia. No-existente → el mismo 404 genérico de
     * {@code AccesoService.exigirMembresia}.
     */
    private void bloquearOrganizacion(UUID organizacionId) {
        organizacionRepository.findConBloqueoById(organizacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Organización no encontrada o no tiene permisos"));
    }

    private Membresia membresiaEnOrganizacion(UUID organizacionId, UUID usuarioId) {
        return membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Miembro no encontrado en esta organización"));
    }

    private OrganizacionResponseDTO toOrganizacionDTO(Membresia m) {
        Organizacion organizacion = m.getOrganizacion();
        return new OrganizacionResponseDTO(
                organizacion.getId(), organizacion.getNombre(), m.getRolGestion(), m.isEsTerapeuta());
    }

    private MiembroResponseDTO toMiembroDTO(Membresia m) {
        Usuario usuario = m.getUsuario();
        return new MiembroResponseDTO(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(),
                m.getRolGestion(), m.isEsTerapeuta(), m.getUnidoEn());
    }
}
