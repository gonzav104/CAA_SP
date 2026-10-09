package com.caa.api.services.impl;

import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.services.AccesoService;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementación única de {@link AccesoService} (design-part2 §11.1). Resuelve el acceso en como
 * máximo 4 consultas: paciente, membresía, (condicional) asignación clínica, (condicional)
 * vínculo familiar. Nunca cachea entre llamadas ni asume una "organización activa".
 */
@Service
@RequiredArgsConstructor
public class AccesoServiceImpl implements AccesoService {

    private final MembresiaRepository membresiaRepository;
    private final PacienteRepository pacienteRepository;
    private final PacienteTerapeutaRepository pacienteTerapeutaRepository;
    private final PacienteFamiliarRepository pacienteFamiliarRepository;

    @Override
    public Membresia exigirMembresia(UUID organizacionId, Usuario usuario) {
        return membresiaRepository.findById(new MembresiaId(organizacionId, usuario.getId()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Organización no encontrada o no tiene permisos"));
    }

    @Override
    public Membresia exigirRolGestion(UUID organizacionId, Usuario usuario, Set<RolGestion> permitidos) {
        Membresia membresia = exigirMembresia(organizacionId, usuario);
        if (!permitidos.contains(membresia.getRolGestion())) {
            throw new AccesoDenegadoException("No tiene el rol de gestión requerido para esta operación");
        }
        return membresia;
    }

    @Override
    public Membresia exigirAltaPaciente(UUID organizacionId, Usuario usuario) {
        Membresia membresia = exigirMembresia(organizacionId, usuario);
        if (!membresia.esGestion() && !membresia.isEsTerapeuta()) {
            throw new AccesoDenegadoException(
                    "No tiene permisos para registrar pacientes en esta organización");
        }
        return membresia;
    }

    @Override
    public AccesoPaciente resolverAccesoPaciente(UUID pacienteId, Usuario usuario) {
        // 1. Paciente.
        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Paciente no encontrado o no tiene permisos"));

        // 2. Membresía del usuario en la organización REAL del paciente (nunca en otra).
        Membresia membresia = null;
        if (paciente.getOrganizacion() != null) {
            UUID organizacionId = paciente.getOrganizacion().getId();
            membresia = membresiaRepository.findById(new MembresiaId(organizacionId, usuario.getId()))
                    .orElse(null);
        }

        // 3. Asignación clínica, solo si hace falta para decidir acceso de equipo (los miembros de
        // gestión ya tienen acceso organizacional completo sin necesitar esta fila).
        boolean asignado = false;
        if (membresia != null && !membresia.esGestion() && membresia.isEsTerapeuta()) {
            asignado = pacienteTerapeutaRepository.existsById(new PacienteTerapeutaId(pacienteId, usuario.getId()));
        }

        AccesoPaciente acceso = new AccesoPaciente(paciente, membresia, asignado, null);
        if (acceso.esEquipo()) {
            return acceso;
        }

        // 4. Vínculo familiar, solo si no hay acceso de equipo.
        PermisoColaborador permisoFamiliar = pacienteFamiliarRepository
                .findByPaciente_IdAndUsuario_Id(pacienteId, usuario.getId())
                .map(PacienteFamiliar::getPermiso)
                .orElse(null);

        if (permisoFamiliar == null) {
            throw new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos");
        }

        return new AccesoPaciente(paciente, membresia, false, permisoFamiliar);
    }

    @Override
    public AccesoPaciente exigirCapacidad(UUID pacienteId, Usuario usuario, Capacidad capacidad) {
        AccesoPaciente acceso = resolverAccesoPaciente(pacienteId, usuario);
        if (!tieneCapacidad(acceso, capacidad)) {
            throw new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos");
        }
        return acceso;
    }

    /** Matriz de capacidades de design-part2 §11.1. */
    private boolean tieneCapacidad(AccesoPaciente acceso, Capacidad capacidad) {
        return switch (capacidad) {
            case LEER -> acceso.esEquipo() || acceso.permisoFamiliar() != null;
            case EDITAR_CONTENIDO -> acceso.esEquipo()
                    || acceso.permisoFamiliar() == PermisoColaborador.EDICION_LIMITADA;
            case GESTION_CLINICA -> acceso.esEquipo();
            case ADMINISTRAR -> acceso.esGestion();
        };
    }
}
