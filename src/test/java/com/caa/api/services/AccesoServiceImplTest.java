package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PacienteTerapeuta;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.AccesoServiceImpl;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Matriz de resolución de acceso de {@code AccesoServiceImpl} (design-part2 §11.1): producto
 * cruzado rolGestion {@code {OWNER, ADMIN, MIEMBRO}} x esTerapeuta {@code {true, false}} x
 * asignado {@code {sí, no}}, más no-miembro, cross-org, familiar LECTURA/EDICION_LIMITADA y
 * staff+familiar simultáneos. Cubre {@code resolverAccesoPaciente} y {@code exigirCapacidad}.
 * {@code exigirMembresia}, {@code exigirRolGestion} y {@code exigirAltaPaciente} se cubren en
 * {@code AccesoServiceExigirAltaPacienteTest} (tarea 2.3/2.4).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccesoServiceImpl — resolverAccesoPaciente / exigirCapacidad")
class AccesoServiceImplTest {

    @Mock private MembresiaRepository membresiaRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private PacienteTerapeutaRepository pacienteTerapeutaRepository;
    @Mock private PacienteFamiliarRepository pacienteFamiliarRepository;

    @InjectMocks private AccesoServiceImpl accesoService;

    private UUID organizacionId;
    private UUID otraOrganizacionId;
    private UUID usuarioId;
    private UUID pacienteId;
    private Organizacion organizacion;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        organizacionId = UUID.randomUUID();
        otraOrganizacionId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();

        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder().id(pacienteId).organizacion(organizacion).build();
    }

    private Membresia membresia(RolGestion rolGestion, boolean esTerapeuta) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, usuarioId))
                .organizacion(organizacion)
                .usuario(Usuario.builder().id(usuarioId).build())
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build();
    }

    /**
     * Fila por fila de la matriz de capacidades (design-part2 §11.1): columnas "Gestion" y
     * "Clinical assigned" cubiertas aquí; "member without staff access", "familiar
     * EDICION_LIMITADA" y "familiar LECTURA" tienen sus propios tests dedicados más abajo.
     */
    static Stream<Arguments> matrizGestionYClinico() {
        return Stream.of(
                // rolGestion, esTerapeuta, asignado, capacidad, permitido
                Arguments.of(RolGestion.OWNER, true, true, Capacidad.LEER, true),
                Arguments.of(RolGestion.OWNER, true, false, Capacidad.LEER, true),
                Arguments.of(RolGestion.OWNER, false, false, Capacidad.LEER, true),
                Arguments.of(RolGestion.ADMIN, true, true, Capacidad.LEER, true),
                Arguments.of(RolGestion.ADMIN, false, false, Capacidad.LEER, true),
                Arguments.of(RolGestion.OWNER, true, true, Capacidad.EDITAR_CONTENIDO, true),
                Arguments.of(RolGestion.ADMIN, false, false, Capacidad.EDITAR_CONTENIDO, true),
                Arguments.of(RolGestion.OWNER, false, false, Capacidad.GESTION_CLINICA, true),
                Arguments.of(RolGestion.ADMIN, true, true, Capacidad.GESTION_CLINICA, true),
                Arguments.of(RolGestion.OWNER, true, true, Capacidad.ADMINISTRAR, true),
                Arguments.of(RolGestion.ADMIN, false, false, Capacidad.ADMINISTRAR, true),
                // MIEMBRO asignado (esTerapeuta=true + fila PacienteTerapeuta): acceso clínico, nunca ADMINISTRAR
                Arguments.of(RolGestion.MIEMBRO, true, true, Capacidad.LEER, true),
                Arguments.of(RolGestion.MIEMBRO, true, true, Capacidad.EDITAR_CONTENIDO, true),
                Arguments.of(RolGestion.MIEMBRO, true, true, Capacidad.GESTION_CLINICA, true),
                Arguments.of(RolGestion.MIEMBRO, true, true, Capacidad.ADMINISTRAR, false),
                // MIEMBRO con esTerapeuta=true pero SIN fila PacienteTerapeuta (no asignado a ESTE paciente): sin acceso clínico
                Arguments.of(RolGestion.MIEMBRO, true, false, Capacidad.LEER, false),
                Arguments.of(RolGestion.MIEMBRO, true, false, Capacidad.GESTION_CLINICA, false),
                // MIEMBRO con esTerapeuta=false: nunca tiene acceso clínico, sin importar "asignado"
                Arguments.of(RolGestion.MIEMBRO, false, false, Capacidad.LEER, false),
                Arguments.of(RolGestion.MIEMBRO, false, false, Capacidad.GESTION_CLINICA, false),
                Arguments.of(RolGestion.MIEMBRO, false, false, Capacidad.ADMINISTRAR, false)
        );
    }

    @ParameterizedTest(name = "{0} esTerapeuta={1} asignado={2} capacidad={3} → permitido={4}")
    @MethodSource("matrizGestionYClinico")
    @DisplayName("Matriz rolGestion x esTerapeuta x asignado x Capacidad")
    void matrizCapacidades(RolGestion rolGestion, boolean esTerapeuta, boolean asignado,
                            Capacidad capacidad, boolean permitido) {
        Membresia m = membresia(rolGestion, esTerapeuta);
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.of(m));
        if (!m.esGestion() && m.isEsTerapeuta()) {
            given(pacienteTerapeutaRepository.existsById(new PacienteTerapeutaId(pacienteId, usuarioId)))
                    .willReturn(asignado);
        }

        Usuario usuario = Usuario.builder().id(usuarioId).build();

        if (permitido) {
            AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, usuario, capacidad);
            assertThat(acceso.paciente()).isEqualTo(paciente);
        } else {
            assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, usuario, capacidad))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("no tiene permisos");
        }
    }

    @Test
    @DisplayName("No-miembro de la organización del paciente → 404 genérico")
    void noMiembro_lanza404() {
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.empty());
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId))
                .willReturn(Optional.empty());

        Usuario usuario = Usuario.builder().id(usuarioId).build();

        assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(pacienteTerapeutaRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("Miembro de OTRA organización (cross-org) → 404, nunca se filtra al paciente de otra org")
    void miembroDeOtraOrganizacion_lanza404() {
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        // La membresía del usuario existe, pero para otraOrganizacionId, no para la del paciente:
        // el lookup por la clave compuesta (organizacionId del paciente, usuarioId) no la encuentra.
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.empty());
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId))
                .willReturn(Optional.empty());

        Usuario usuario = Usuario.builder().id(usuarioId).build();

        assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.ADMINISTRAR))
                .isInstanceOf(RecursoNoEncontradoException.class);
        // La membresía en otraOrganizacionId jamás se consulta: la resolución usa exclusivamente
        // la organización real del paciente.
        assertThat(otraOrganizacionId).isNotEqualTo(organizacionId);
    }

    @Test
    @DisplayName("Paciente inexistente → 404 genérico, sin consultar membresías")
    void pacienteInexistente_lanza404() {
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.empty());

        Usuario usuario = Usuario.builder().id(usuarioId).build();

        assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(membresiaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Familiar LECTURA → LEER permitido, EDITAR_CONTENIDO y GESTION_CLINICA denegados (404)")
    void familiarLectura_soloLeer() {
        PacienteFamiliar vinculo = PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, usuarioId))
                .paciente(paciente)
                .usuario(Usuario.builder().id(usuarioId).build())
                .permiso(PermisoColaborador.LECTURA)
                .build();
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.empty());
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId))
                .willReturn(Optional.of(vinculo));

        Usuario usuario = Usuario.builder().id(usuarioId).build();

        assertThat(accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER)).isNotNull();
        assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.EDITAR_CONTENIDO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Familiar EDICION_LIMITADA → LEER y EDITAR_CONTENIDO permitidos, GESTION_CLINICA denegado (404)")
    void familiarEdicionLimitada_leerYEditar() {
        PacienteFamiliar vinculo = PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, usuarioId))
                .paciente(paciente)
                .usuario(Usuario.builder().id(usuarioId).build())
                .permiso(PermisoColaborador.EDICION_LIMITADA)
                .build();
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.empty());
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, usuarioId))
                .willReturn(Optional.of(vinculo));

        Usuario usuario = Usuario.builder().id(usuarioId).build();

        assertThat(accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.LEER)).isNotNull();
        assertThat(accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.EDITAR_CONTENIDO)).isNotNull();
        assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, usuario, Capacidad.GESTION_CLINICA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Staff asignado (MIEMBRO + esTerapeuta=true) Y familiar simultáneos → gana el acceso de equipo")
    void staffYFamiliarSimultaneos_ganaStaff() {
        Membresia m = membresia(RolGestion.MIEMBRO, true);
        PacienteFamiliar vinculo = PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, usuarioId))
                .paciente(paciente)
                .usuario(Usuario.builder().id(usuarioId).build())
                .permiso(PermisoColaborador.LECTURA)
                .build();

        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.of(m));
        given(pacienteTerapeutaRepository.existsById(new PacienteTerapeutaId(pacienteId, usuarioId)))
                .willReturn(true);

        Usuario usuario = Usuario.builder().id(usuarioId).build();

        AccesoPaciente acceso = accesoService.resolverAccesoPaciente(pacienteId, usuario);

        assertThat(acceso.esClinicoAsignado()).isTrue();
        assertThat(acceso.permisoFamiliar()).isNull();
        // El check familiar nunca se consulta porque esEquipo() ya es true (resolución de 4 queries máximo).
        verify(pacienteFamiliarRepository, never()).findByPaciente_IdAndUsuario_Id(any(), any());
    }
}
