package com.caa.api.services;

import com.caa.api.models.Membresia;
import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import java.util.Set;
import java.util.UUID;

/**
 * Único componente de resolución de autorización multi-tenant (design-part2 §11.1): responde
 * "qué puede hacer este {@link Usuario} sobre esta organización / este paciente", resuelto en
 * cada llamada desde la base de datos (sin estado de tenant en JWT/sesión). Sustituye a todo
 * las capacidades a partir de las membresías y relaciones clínicas persistidas.
 * <p>
 * {@code rolGestion} y {@code esTerapeuta} son atributos INDEPENDIENTES de {@link Membresia}:
 * ningún método de esta interfaz ni de su implementación puede tratarlos como mutuamente
 * excluyentes ni derivar uno del otro.
 */
public interface AccesoService {

    /**
     * Exige que el usuario sea miembro de la organización. No-miembro →
     * {@code RecursoNoEncontradoException("Organización no encontrada o no tiene permisos")}.
     */
    Membresia exigirMembresia(UUID organizacionId, Usuario usuario);

    /**
     * Exige que el usuario sea miembro de la organización CON un {@code rolGestion} dentro de
     * {@code permitidos}. Miembro cuyo rolGestion no está permitido →
     * {@code AccesoDenegadoException} (403). No-miembro → el mismo 404 de {@link #exigirMembresia}.
     */
    Membresia exigirRolGestion(UUID organizacionId, Usuario usuario, Set<RolGestion> permitidos);

    /**
     * Exige la capacidad de alta de pacientes (design part 1 §10.3): {@code rolGestion} en
     * {OWNER, ADMIN} O {@code esTerapeuta = true}. Miembro sin ninguna de las dos →
     * {@code AccesoDenegadoException} (403). No-miembro → el 404 de {@link #exigirMembresia}.
     */
    Membresia exigirAltaPaciente(UUID organizacionId, Usuario usuario);

    /**
     * Resuelve el acceso del usuario sobre el paciente (gestión, clínico asignado o familiar),
     * en como máximo 4 consultas (design-part2 §11.1). Sin acceso de ningún tipo →
     * {@code RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos")}.
     */
    AccesoPaciente resolverAccesoPaciente(UUID pacienteId, Usuario usuario);

    /**
     * Resuelve el acceso y exige la {@link Capacidad} solicitada. Acceso insuficiente → el mismo
     * 404 genérico que {@link #resolverAccesoPaciente} sin acceso alguno (preserva el contrato de
     * error existente: nunca se revela si el paciente existe cuando el llamante no tiene permiso).
     */
    AccesoPaciente exigirCapacidad(UUID pacienteId, Usuario usuario, Capacidad capacidad);

    /**
     * Resultado de la resolución de acceso sobre un paciente (design-part2 §11.1).
     *
     * @param paciente        el paciente resuelto.
     * @param membresia       la {@link Membresia} del usuario en la organización del paciente, o
     *                        {@code null} si el usuario no es miembro de esa organización.
     * @param asignado        {@code true} si existe una fila {@code PacienteTerapeuta} para
     *                         (paciente, usuario) — solo tiene sentido cuando {@code membresia}
     *                         no es de gestión y {@code esTerapeuta = true}.
     * @param permisoFamiliar el {@link PermisoColaborador} efectivo vía {@code PacienteFamiliar},
     *                        o {@code null} si el acceso efectivo no es familiar (incluye el caso
     *                        en que el acceso de equipo ya resuelve la solicitud).
     */
    record AccesoPaciente(Paciente paciente, Membresia membresia, boolean asignado,
                          PermisoColaborador permisoFamiliar) {

        /** Acceso de gestión: {@code rolGestion} OWNER/ADMIN de la organización del paciente. */
        public boolean esGestion() {
            return membresia != null && membresia.esGestion();
        }

        /** Acceso clínico asignado: {@code esTerapeuta = true} Y fila {@code PacienteTerapeuta}. */
        public boolean esClinicoAsignado() {
            return membresia != null && membresia.isEsTerapeuta() && asignado;
        }

        /** Acceso de equipo (gestión o clínico asignado), independiente del acceso familiar. */
        public boolean esEquipo() {
            return esGestion() || esClinicoAsignado();
        }
    }

    /** Capacidades sobre un paciente, decididas por la matriz de design-part2 §11.1. */
    enum Capacidad {
        LEER,
        EDITAR_CONTENIDO,
        GESTION_CLINICA,
        ADMINISTRAR
    }
}
