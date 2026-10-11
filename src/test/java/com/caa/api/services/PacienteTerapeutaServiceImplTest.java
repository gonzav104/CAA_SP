package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.caa.api.dtos.AsignacionTerapeutaDTO;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteTerapeuta;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.PacienteTerapeutaServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitarios de {@code PacienteTerapeutaServiceImpl} (design-part2 §12.2, §13; tareas
 * 5.14-5.16). {@code AccesoService} se mockea directamente: su propia matriz de resolución vive
 * en {@code AccesoServiceImplTest}, no se reimplementa aquí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PacienteTerapeutaServiceImpl — asignación clínica terapeuta↔paciente")
class PacienteTerapeutaServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MembresiaRepository membresiaRepository;
    @Mock private PacienteTerapeutaRepository pacienteTerapeutaRepository;
    @Mock private OrganizacionRepository organizacionRepository;
    @Mock private AccesoService accesoService;

    @InjectMocks private PacienteTerapeutaServiceImpl service;

    private UUID organizacionId;
    private UUID pacienteId;
    private UUID callerId;
    private UUID objetivoId;
    private String emailCaller;
    private Usuario caller;
    private Usuario objetivo;
    private Organizacion organizacion;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        organizacionId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        objetivoId = UUID.randomUUID();
        emailCaller = "owner@ejemplo.com";

        caller = Usuario.builder().id(callerId).email(emailCaller).nombre("Owner").build();
        objetivo = Usuario.builder().id(objetivoId).email("terapeuta@ejemplo.com").nombre("Terapeuta").build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder().id(pacienteId).organizacion(organizacion).nombre("Nico").build();

        given(usuarioRepository.findByEmail(emailCaller)).willReturn(Optional.of(caller));
    }

    private Membresia membresia(Usuario usuario, RolGestion rolGestion, boolean esTerapeuta) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, usuario.getId()))
                .organizacion(organizacion)
                .usuario(usuario)
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build();
    }

    // ──────────────────────────────────────────────
    //  listarAsignados
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listarAsignados exige Capacidad.GESTION_CLINICA y mapea el roster")
    void listarAsignados_devuelveRoster() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.GESTION_CLINICA))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.OWNER, true), false, null));
        PacienteTerapeuta pt = PacienteTerapeuta.builder()
                .id(new PacienteTerapeutaId(pacienteId, objetivoId))
                .paciente(paciente)
                .usuario(objetivo)
                .organizacionId(organizacionId)
                .build();
        given(pacienteTerapeutaRepository.findByPaciente_Id(pacienteId)).willReturn(List.of(pt));

        var result = service.listarAsignados(pacienteId, emailCaller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).usuarioId()).isEqualTo(objetivoId);
        assertThat(result.get(0).email()).isEqualTo("terapeuta@ejemplo.com");
    }

    @Test
    @DisplayName("listarAsignados sin acceso de equipo → 404 genérico (delegado por AccesoService)")
    void listarAsignados_sinAcceso_lanza404() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.GESTION_CLINICA))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> service.listarAsignados(pacienteId, emailCaller))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(pacienteTerapeutaRepository, never()).findByPaciente_Id(any());
    }

    // ──────────────────────────────────────────────
    //  asignar
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("asignar: objetivo miembro con esTerapeuta=true → crea la asignación")
    void asignar_objetivoValido_creaAsignacion() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.OWNER, true), false, null));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, objetivoId)))
                .willReturn(Optional.of(membresia(objetivo, RolGestion.MIEMBRO, true)));
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, objetivoId);
        given(pacienteTerapeutaRepository.findById(id)).willReturn(Optional.empty());
        given(pacienteTerapeutaRepository.saveAndFlush(any(PacienteTerapeuta.class))).willAnswer(inv -> inv.getArgument(0));

        var result = service.asignar(pacienteId, new AsignacionTerapeutaDTO(objetivoId), emailCaller);

        assertThat(result.usuarioId()).isEqualTo(objetivoId);
        verify(pacienteTerapeutaRepository).saveAndFlush(any(PacienteTerapeuta.class));
    }

    @Test
    @DisplayName("asignar: objetivo con esTerapeuta=false → 409, no crea fila")
    void asignar_objetivoSinCapacidadClinica_lanzaConflicto() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.OWNER, true), false, null));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, objetivoId)))
                .willReturn(Optional.of(membresia(objetivo, RolGestion.MIEMBRO, false)));

        assertThatThrownBy(() -> service.asignar(pacienteId, new AsignacionTerapeutaDTO(objetivoId), emailCaller))
                .isInstanceOf(ConflictoException.class);

        verify(pacienteTerapeutaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("asignar: objetivo no es miembro de la organización del paciente → 404 genérico")
    void asignar_objetivoNoEsMiembro_lanza404() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.OWNER, true), false, null));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, objetivoId)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.asignar(pacienteId, new AsignacionTerapeutaDTO(objetivoId), emailCaller))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(pacienteTerapeutaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("asignar: llamante sin rolGestion de gestión → 404 genérico (ADMINISTRAR vía AccesoService)")
    void asignar_llamanteSinGestion_lanza404() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> service.asignar(pacienteId, new AsignacionTerapeutaDTO(objetivoId), emailCaller))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(organizacionRepository, never()).findConBloqueoById(any());
    }

    @Test
    @DisplayName("asignar: asignación duplicada es idempotente, no crea una segunda fila")
    void asignar_duplicado_esIdempotente() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.OWNER, true), false, null));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, objetivoId)))
                .willReturn(Optional.of(membresia(objetivo, RolGestion.MIEMBRO, true)));
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, objetivoId);
        PacienteTerapeuta existente = PacienteTerapeuta.builder()
                .id(id).paciente(paciente).usuario(objetivo).organizacionId(organizacionId).build();
        given(pacienteTerapeutaRepository.findById(id)).willReturn(Optional.of(existente));

        service.asignar(pacienteId, new AsignacionTerapeutaDTO(objetivoId), emailCaller);

        verify(pacienteTerapeutaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("asignar: MIEMBRO con esTerapeuta=true (sin rolGestion de gestión) no puede autoasignarse")
    void asignar_miembroSinGestion_lanza404() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> service.asignar(pacienteId, new AsignacionTerapeutaDTO(objetivoId), emailCaller))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("asignar: ADMIN puro (esTerapeuta=false) PUEDE asignar a otro miembro")
    void asignar_adminPuro_puedeAsignar() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.ADMIN, false), false, null));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, objetivoId)))
                .willReturn(Optional.of(membresia(objetivo, RolGestion.MIEMBRO, true)));
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, objetivoId);
        given(pacienteTerapeutaRepository.findById(id)).willReturn(Optional.empty());
        given(pacienteTerapeutaRepository.saveAndFlush(any(PacienteTerapeuta.class))).willAnswer(inv -> inv.getArgument(0));

        var result = service.asignar(pacienteId, new AsignacionTerapeutaDTO(objetivoId), emailCaller);

        assertThat(result.usuarioId()).isEqualTo(objetivoId);
    }

    // ──────────────────────────────────────────────
    //  desasignar
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("desasignar: fila existente → se elimina")
    void desasignar_filaExistente_elimina() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.OWNER, true), false, null));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, objetivoId);
        given(pacienteTerapeutaRepository.existsById(id)).willReturn(true);

        service.desasignar(pacienteId, objetivoId, emailCaller);

        verify(pacienteTerapeutaRepository).deleteById(id);
    }

    @Test
    @DisplayName("desasignar: fila inexistente → no-op, idempotente")
    void desasignar_filaInexistente_noOp() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willReturn(new AccesoPaciente(paciente, membresia(caller, RolGestion.OWNER, true), false, null));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        PacienteTerapeutaId id = new PacienteTerapeutaId(pacienteId, objetivoId);
        given(pacienteTerapeutaRepository.existsById(id)).willReturn(false);

        service.desasignar(pacienteId, objetivoId, emailCaller);

        verify(pacienteTerapeutaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("desasignar: sin capacidad ADMINISTRAR → 404 genérico, sin bloquear la organización")
    void desasignar_sinAdministrar_lanza404() {
        given(accesoService.exigirCapacidad(pacienteId, caller, Capacidad.ADMINISTRAR))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> service.desasignar(pacienteId, objetivoId, emailCaller))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(organizacionRepository, never()).findConBloqueoById(any());
    }
}
