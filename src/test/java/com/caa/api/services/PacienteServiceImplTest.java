package com.caa.api.services;

import com.caa.api.dtos.PacienteActualizacionDTO;
import com.caa.api.dtos.PacienteRegistroDTO;
import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
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
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.PacienteServiceImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tarea 2.9: fixtures reescritas al idiom del fixture-helper compartido
 * {@code membresiaDe(rolGestion, esTerapeuta)} — nunca un único parámetro "rol" (design-part2
 * §13). {@code PacienteServiceImplTest} orquesta sobre {@code AccesoService} ya mockeado: la
 * matriz de resolución de acceso en sí vive en {@code AccesoServiceImplTest} /
 * {@code AccesoServiceExigirAltaPacienteTest}, no se reimplementa aquí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PacienteServiceImpl — creación, listados y get/update/delete multi-tenant")
class PacienteServiceImplTest {

    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteFamiliarRepository pacienteFamiliarRepository;
    @Mock private AccesoService accesoService;
    @Mock private PacienteTerapeutaRepository pacienteTerapeutaRepository;
    @Mock private MembresiaRepository membresiaRepository;

    @InjectMocks private PacienteServiceImpl pacienteService;

    private UUID pacienteId;
    private UUID terapeutaId;
    private UUID familiarId;
    private UUID organizacionId;
    private Usuario terapeuta;
    private Usuario familiar;
    private Paciente paciente;
    private Organizacion organizacion;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        terapeutaId = UUID.randomUUID();
        familiarId = UUID.randomUUID();
        organizacionId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).build();
        familiar = Usuario.builder().id(familiarId).build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2020, 5, 10))
                .creadoEn(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private PacienteFamiliar vinculo() {
        return PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, familiarId))
                .paciente(paciente)
                .usuario(familiar)
                .permiso(PermisoColaborador.LECTURA)
                .build();
    }

    /**
     * Fixture-helper compartido (design-part2 §13, tarea 2.9): parámetros EXPLÍCITOS
     * {@code (rolGestion, esTerapeuta)}, nunca un único parámetro "rol". Se introduce aquí y se
     * reutiliza, sin reimplementarse, en todos los tests de este archivo y en los de fases
     * posteriores que necesiten una {@code Membresia}.
     */
    private Membresia membresiaDe(RolGestion rolGestion, boolean esTerapeuta) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeutaId))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build();
    }

    // ──────────────────────────────────────────────
    //  GET /api/pacientes/{id} — vía AccesoService.exigirCapacidad(LEER)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo (gestión) → obtiene el paciente")
    void miembroDeEquipo_obtienePaciente() {
        AccesoPaciente acceso = new AccesoPaciente(paciente, membresiaDe(RolGestion.OWNER, true), false, null);
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER)).willReturn(acceso);

        PacienteResponseDTO dto = pacienteService.obtenerPaciente(pacienteId, "terapeuta@ejemplo.com");

        assertThat(dto.id()).isEqualTo(pacienteId);
        assertThat(dto.nombre()).isEqualTo("Nico");
        assertThat(dto.apellido()).isEqualTo("Perez");
        assertThat(dto.fechaNacimiento()).isEqualTo(LocalDate.of(2020, 5, 10));
    }

    @Test
    @DisplayName("Familiar asignado → obtiene el paciente")
    void familiarAsignado_obtienePaciente() {
        AccesoPaciente acceso = new AccesoPaciente(paciente, null, false, PermisoColaborador.LECTURA);
        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER)).willReturn(acceso);

        PacienteResponseDTO dto = pacienteService.obtenerPaciente(pacienteId, "familiar@ejemplo.com");

        assertThat(dto.id()).isEqualTo(pacienteId);
        assertThat(dto.nombre()).isEqualTo("Nico");
        assertThat(dto.apellido()).isEqualTo("Perez");
    }

    @Test
    @DisplayName("Miembro de equipo → miPermiso es null")
    void miembroDeEquipo_miPermisoNull() {
        AccesoPaciente acceso = new AccesoPaciente(paciente, membresiaDe(RolGestion.OWNER, true), false, null);
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER)).willReturn(acceso);

        PacienteResponseDTO dto = pacienteService.obtenerPaciente(pacienteId, "terapeuta@ejemplo.com");

        assertThat(dto.miPermiso()).isNull();
    }

    @Test
    @DisplayName("Familiar con EDICION_LIMITADA → miPermiso es EDICION_LIMITADA")
    void familiarEdicionLimitada_miPermisoEdicionLimitada() {
        AccesoPaciente acceso = new AccesoPaciente(paciente, null, false, PermisoColaborador.EDICION_LIMITADA);
        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER)).willReturn(acceso);

        PacienteResponseDTO dto = pacienteService.obtenerPaciente(pacienteId, "familiar@ejemplo.com");

        assertThat(dto.miPermiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);
    }

    @Test
    @DisplayName("Familiar con LECTURA → miPermiso es LECTURA")
    void familiarLectura_miPermisoLectura() {
        AccesoPaciente acceso = new AccesoPaciente(paciente, null, false, PermisoColaborador.LECTURA);
        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER)).willReturn(acceso);

        PacienteResponseDTO dto = pacienteService.obtenerPaciente(pacienteId, "familiar@ejemplo.com");

        assertThat(dto.miPermiso()).isEqualTo(PermisoColaborador.LECTURA);
    }

    @Test
    @DisplayName("Paciente inexistente o ajeno → lanza excepción 404 (propagada desde AccesoService)")
    void pacienteInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> pacienteService.obtenerPaciente(pacienteId, "terapeuta@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
    }

    @Test
    @DisplayName("Usuario inexistente → lanza 404 genérico \"Usuario no encontrado\"")
    void usuarioInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.obtenerPaciente(pacienteId, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    // ──────────────────────────────────────────────
    //  PUT / DELETE /api/pacientes/{id} — vía AccesoService.exigirCapacidad
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("actualizarPaciente con principal inexistente → 404 genérico \"Usuario no encontrado\"")
    void actualizarPaciente_principalInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        PacienteActualizacionDTO dto = new PacienteActualizacionDTO("Nico", "Perez", LocalDate.of(2020, 5, 10), null);

        assertThatThrownBy(() -> pacienteService.actualizarPaciente(pacienteId, dto, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    @DisplayName("eliminarPaciente con principal inexistente → 404 genérico \"Usuario no encontrado\"")
    void eliminarPaciente_principalInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.eliminarPaciente(pacienteId, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    @DisplayName("eliminarPaciente: ADMINISTRAR (OWNER/ADMIN) → borra el paciente")
    void eliminarPaciente_administrar_borraPaciente() {
        AccesoPaciente acceso = new AccesoPaciente(paciente, membresiaDe(RolGestion.OWNER, false), false, null);
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.ADMINISTRAR)).willReturn(acceso);

        pacienteService.eliminarPaciente(pacienteId, "terapeuta@ejemplo.com");

        verify(pacienteRepository).delete(paciente);
    }

    @Test
    @DisplayName("eliminarPaciente: MIEMBRO clínico asignado (no ADMINISTRAR) → 404, AccesoService lo rechaza")
    void eliminarPaciente_miembroClinicoAsignado_lanza404() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.ADMINISTRAR))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> pacienteService.eliminarPaciente(pacienteId, "terapeuta@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(pacienteRepository, never()).delete(any());
    }

    // ──────────────────────────────────────────────
    //  GET /api/pacientes (unión: gestión + clínico asignado + familiar) — tarea 2.9
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("obtenerMisPacientes: miembro de gestión (OWNER/ADMIN) → lista el workspace, miPermiso null")
    void obtenerMisPacientes_gestion() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(membresiaRepository.findByUsuario_Id(terapeutaId))
                .willReturn(List.of(membresiaDe(RolGestion.OWNER, true)));
        given(pacienteRepository.findAccesiblesPorEquipo(Set.of(organizacionId), Set.of(), terapeutaId))
                .willReturn(List.of(paciente));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerMisPacientes("terapeuta@ejemplo.com");

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().id()).isEqualTo(pacienteId);
        assertThat(dtos.getFirst().miPermiso()).isNull();
    }

    @Test
    @DisplayName("obtenerMisPacientes: MIEMBRO con esTerapeuta=true (clínico) → entra en orgIdsClinicas, no en orgIdsGestion")
    void obtenerMisPacientes_clinicoAsignado() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(membresiaRepository.findByUsuario_Id(terapeutaId))
                .willReturn(List.of(membresiaDe(RolGestion.MIEMBRO, true)));
        given(pacienteRepository.findAccesiblesPorEquipo(Set.of(), Set.of(organizacionId), terapeutaId))
                .willReturn(List.of(paciente));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerMisPacientes("terapeuta@ejemplo.com");

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().id()).isEqualTo(pacienteId);
    }

    @Test
    @DisplayName("obtenerMisPacientes: MIEMBRO con esTerapeuta=false (sin membresías de ningún tipo relevante) → consulta de equipo se OMITE por completo")
    void obtenerMisPacientes_sinAccesoDeEquipo_omiteConsulta() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(membresiaRepository.findByUsuario_Id(terapeutaId))
                .willReturn(List.of(membresiaDe(RolGestion.MIEMBRO, false)));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerMisPacientes("terapeuta@ejemplo.com");

        assertThat(dtos).isEmpty();
        verify(pacienteRepository, never()).findAccesiblesPorEquipo(any(), any(), any());
    }

    @Test
    @DisplayName("obtenerMisPacientes para FAMILIAR → lista sus pacientes vinculados con permiso")
    void obtenerMisPacientes_familiar() {
        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(membresiaRepository.findByUsuario_Id(familiarId)).willReturn(List.of());
        given(pacienteFamiliarRepository.findByUsuario_Id(familiarId)).willReturn(List.of(vinculo()));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerMisPacientes("familiar@ejemplo.com");

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().id()).isEqualTo(pacienteId);
        assertThat(dtos.getFirst().miPermiso()).isEqualTo(PermisoColaborador.LECTURA);
        verify(pacienteRepository, never()).findAccesiblesPorEquipo(any(), any(), any());
    }

    @Test
    @DisplayName("obtenerMisPacientes: acceso de equipo Y familiar simultáneos para el mismo paciente → gana equipo (miPermiso null)")
    void obtenerMisPacientes_equipoYFamiliar_ganaEquipo() {
        PacienteFamiliar vinculoSobreMismoPaciente = PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, terapeutaId))
                .paciente(paciente)
                .usuario(terapeuta)
                .permiso(PermisoColaborador.LECTURA)
                .build();

        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(membresiaRepository.findByUsuario_Id(terapeutaId))
                .willReturn(List.of(membresiaDe(RolGestion.OWNER, true)));
        given(pacienteRepository.findAccesiblesPorEquipo(Set.of(organizacionId), Set.of(), terapeutaId))
                .willReturn(List.of(paciente));
        given(pacienteFamiliarRepository.findByUsuario_Id(terapeutaId))
                .willReturn(List.of(vinculoSobreMismoPaciente));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerMisPacientes("terapeuta@ejemplo.com");

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().miPermiso()).isNull();
    }

    // ──────────────────────────────────────────────
    //  GET /api/organizaciones/{organizacionId}/pacientes — tarea 2.9
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("obtenerPacientesDeOrganizacion: rolGestion OWNER/ADMIN → todo el workspace")
    void obtenerPacientesDeOrganizacion_gestion_todoElWorkspace() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirMembresia(organizacionId, terapeuta))
                .willReturn(membresiaDe(RolGestion.ADMIN, false));
        given(pacienteRepository.findByOrganizacion_Id(organizacionId)).willReturn(List.of(paciente));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerPacientesDeOrganizacion(organizacionId, "terapeuta@ejemplo.com");

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().id()).isEqualTo(pacienteId);
    }

    @Test
    @DisplayName("obtenerPacientesDeOrganizacion: MIEMBRO con esTerapeuta=true → solo lo asignado")
    void obtenerPacientesDeOrganizacion_miembroClinico_soloAsignados() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirMembresia(organizacionId, terapeuta))
                .willReturn(membresiaDe(RolGestion.MIEMBRO, true));
        given(pacienteRepository.findAsignadosEnOrganizacion(organizacionId, terapeutaId)).willReturn(List.of(paciente));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerPacientesDeOrganizacion(organizacionId, "terapeuta@ejemplo.com");

        assertThat(dtos).hasSize(1);
        verify(pacienteRepository, never()).findByOrganizacion_Id(any());
    }

    @Test
    @DisplayName("obtenerPacientesDeOrganizacion: MIEMBRO con esTerapeuta=false → lista vacía, SIN consultar pacientes")
    void obtenerPacientesDeOrganizacion_miembroSinCapacidad_vaciaSinConsulta() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirMembresia(organizacionId, terapeuta))
                .willReturn(membresiaDe(RolGestion.MIEMBRO, false));

        List<PacienteResponseDTO> dtos = pacienteService.obtenerPacientesDeOrganizacion(organizacionId, "terapeuta@ejemplo.com");

        assertThat(dtos).isEmpty();
        verify(pacienteRepository, never()).findByOrganizacion_Id(any());
        verify(pacienteRepository, never()).findAsignadosEnOrganizacion(any(), any());
    }

    @Test
    @DisplayName("obtenerPacientesDeOrganizacion: no-miembro → 404 propagado desde AccesoService")
    void obtenerPacientesDeOrganizacion_noMiembro_lanza404() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirMembresia(organizacionId, terapeuta))
                .willThrow(new RecursoNoEncontradoException("Organización no encontrada o no tiene permisos"));

        assertThatThrownBy(() -> pacienteService.obtenerPacientesDeOrganizacion(organizacionId, "terapeuta@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ──────────────────────────────────────────────
    //  GRID_SIZE: PUT full-replace que CONSERVA si el campo no viene
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("actualizarPaciente con gridSize presente → actualiza la grilla")
    void actualizarPaciente_conGridSize_actualiza() {
        Paciente conGrid = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2020, 5, 10))
                .gridSize(6)
                .build();
        AccesoPaciente acceso = new AccesoPaciente(conGrid, membresiaDe(RolGestion.OWNER, true), false, null);

        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO)).willReturn(acceso);
        given(pacienteRepository.save(any(Paciente.class))).willReturn(conGrid);

        PacienteActualizacionDTO dto = new PacienteActualizacionDTO("Nico", "Perez", LocalDate.of(2020, 5, 10), 6);

        PacienteResponseDTO response = pacienteService.actualizarPaciente(pacienteId, dto, "terapeuta@ejemplo.com");

        verify(pacienteRepository).save(argThat(p -> p.getGridSize() != null && p.getGridSize().equals(6)));
        assertThat(response.gridSize()).isEqualTo(6);
    }

    @Test
    @DisplayName("actualizarPaciente sin gridSize (null) → CONSERVA la grilla existente")
    void actualizarPaciente_sinGridSize_preserva() {
        Paciente conGrid = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2020, 5, 10))
                .gridSize(4)
                .build();
        AccesoPaciente acceso = new AccesoPaciente(conGrid, membresiaDe(RolGestion.OWNER, true), false, null);

        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO)).willReturn(acceso);
        given(pacienteRepository.save(any(Paciente.class))).willReturn(conGrid);

        PacienteActualizacionDTO dto = new PacienteActualizacionDTO("Nico", "Perez", LocalDate.of(2020, 5, 10), null);

        PacienteResponseDTO response = pacienteService.actualizarPaciente(pacienteId, dto, "terapeuta@ejemplo.com");

        verify(pacienteRepository).save(argThat(p -> p.getGridSize() != null && p.getGridSize().equals(4)));
        assertThat(response.gridSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("obtenerPaciente → expone gridSize en la respuesta")
    void obtenerPaciente_exponeGridSize() {
        Paciente conGrid = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2020, 5, 10))
                .gridSize(6)
                .build();
        AccesoPaciente acceso = new AccesoPaciente(conGrid, membresiaDe(RolGestion.OWNER, true), false, null);

        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER)).willReturn(acceso);

        PacienteResponseDTO response = pacienteService.obtenerPaciente(pacienteId, "terapeuta@ejemplo.com");

        assertThat(response.gridSize()).isEqualTo(6);
    }

    // ──────────────────────────────────────────────
    //  Tarea 2.5/2.6: POST /api/organizaciones/{organizacionId}/pacientes
    //  (alta org-scoped, con auto-asignación condicional — design part 1 §10.3)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("registrarPaciente(org): creador MIEMBRO con esTerapeuta=true → crea Paciente + 1 PacienteTerapeuta(creador)")
    void registrarPacienteOrg_miembroClinico_autoAsigna() {
        Membresia membresia = membresiaDe(RolGestion.MIEMBRO, true);

        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirAltaPaciente(organizacionId, terapeuta)).willReturn(membresia);
        given(pacienteRepository.save(any(Paciente.class))).willAnswer(invocation -> {
            Paciente p = invocation.getArgument(0);
            p.setId(pacienteId);
            return p;
        });

        PacienteRegistroDTO dto = new PacienteRegistroDTO("Nico", "Perez", LocalDate.of(2020, 5, 10));

        PacienteResponseDTO resultado = pacienteService.registrarPaciente(organizacionId, dto, "terapeuta@ejemplo.com");

        assertThat(resultado.nombre()).isEqualTo("Nico");
        verify(pacienteRepository).save(argThat(p ->
                p.getTerapeuta() == terapeuta && p.getOrganizacion() == organizacion));
        verify(pacienteTerapeutaRepository).save(argThat(pt ->
                pt.getId().equals(new PacienteTerapeutaId(pacienteId, terapeutaId))
                        && pt.getOrganizacionId().equals(organizacionId)));
    }

    @Test
    @DisplayName("registrarPaciente(org): creador OWNER/ADMIN con esTerapeuta=false → crea Paciente SIN asignación")
    void registrarPacienteOrg_gestionNoClinico_creaSinAsignar() {
        Membresia membresia = membresiaDe(RolGestion.OWNER, false);

        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirAltaPaciente(organizacionId, terapeuta)).willReturn(membresia);
        given(pacienteRepository.save(any(Paciente.class))).willAnswer(invocation -> {
            Paciente p = invocation.getArgument(0);
            p.setId(pacienteId);
            return p;
        });

        PacienteRegistroDTO dto = new PacienteRegistroDTO("Nico", "Perez", LocalDate.of(2020, 5, 10));

        pacienteService.registrarPaciente(organizacionId, dto, "terapeuta@ejemplo.com");

        verify(pacienteTerapeutaRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarPaciente(org): MIEMBRO con esTerapeuta=false → 403, AccesoService.exigirAltaPaciente lo rechaza, sin persistir nada")
    void registrarPacienteOrg_miembroSinCapacidad_rechazado() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirAltaPaciente(organizacionId, terapeuta))
                .willThrow(new AccesoDenegadoException(
                        "No tiene permisos para registrar pacientes en esta organización"));

        PacienteRegistroDTO dto = new PacienteRegistroDTO("Nico", "Perez", LocalDate.of(2020, 5, 10));

        assertThatThrownBy(() -> pacienteService.registrarPaciente(organizacionId, dto, "terapeuta@ejemplo.com"))
                .isInstanceOf(AccesoDenegadoException.class);

        verify(pacienteRepository, never()).save(any());
        verify(pacienteTerapeutaRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarPaciente(org): principal inexistente → 404 genérico \"Usuario no encontrado\"")
    void registrarPacienteOrg_principalInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        PacienteRegistroDTO dto = new PacienteRegistroDTO("Nico", "Perez", LocalDate.of(2020, 5, 10));

        assertThatThrownBy(() -> pacienteService.registrarPaciente(organizacionId, dto, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    /**
     * Rollback cuando falla el INSERT de la asignación DESPUÉS del INSERT del paciente, dentro
     * del mismo límite {@code @Transactional} (tarea 2.5). A nivel de mock (H2/unit) solo se
     * puede probar que la excepción de la asignación se PROPAGA sin ser atrapada y que el método
     * está anotado {@code @Transactional} — la reversión real de la fila {@code Paciente}
     * depende del proxy transaccional de Spring en tiempo de ejecución, lo que requiere una base
     * real. Esa prueba independiente y autoritativa contra PostgreSQL real (incluyendo los FK
     * compuestos de la tarea 1.6b) es {@code PacienteCreationRollbackPostgresIntegrationTest}
     * (tarea 2.6a).
     */
    @Test
    @DisplayName("registrarPaciente(org): falla el INSERT de asignación → la excepción se propaga sin ser atrapada")
    void registrarPacienteOrg_fallaAsignacion_propagaExcepcionSinAtrapar() {
        Membresia membresia = membresiaDe(RolGestion.MIEMBRO, true);

        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirAltaPaciente(organizacionId, terapeuta)).willReturn(membresia);
        given(pacienteRepository.save(any(Paciente.class))).willAnswer(invocation -> {
            Paciente p = invocation.getArgument(0);
            p.setId(pacienteId);
            return p;
        });
        given(pacienteTerapeutaRepository.save(any(PacienteTerapeuta.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("fk_pt_membresia"));

        PacienteRegistroDTO dto = new PacienteRegistroDTO("Nico", "Perez", LocalDate.of(2020, 5, 10));

        assertThatThrownBy(() -> pacienteService.registrarPaciente(organizacionId, dto, "terapeuta@ejemplo.com"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(pacienteService.getClass().getDeclaredMethods())
                .filteredOn(m -> m.getName().equals("registrarPaciente")
                        && m.getParameterCount() == 3)
                .anyMatch(m -> m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }
}
