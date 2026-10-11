package com.caa.api.services;

import com.caa.api.dtos.SesionActualizacionDTO;
import com.caa.api.dtos.SesionRegistroDTO;
import com.caa.api.dtos.SesionResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Sesion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.SesionRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.SesionServiceImpl;
import java.time.LocalDateTime;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Autorización mediante {@code AccesoService.exigirCapacidad(GESTION_CLINICA)}
 * (design-part2 §11.2). La regla funcional exige
 * {@code acceso.esEquipo()} (gestión OWNER/ADMIN o miembro clínico asignado), que nunca es
 * verdadero para un acceso puramente familiar — la exclusión de familiares se preserva.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SesionServiceImpl — Tests unitarios (AccesoService.GESTION_CLINICA)")
class SesionServiceImplTest {

    @Mock private SesionRepository sesionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AccesoService accesoService;

    @InjectMocks private SesionServiceImpl sesionService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID organizacionId;
    private Usuario terapeuta;
    private Organizacion organizacion;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        organizacionId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder().id(pacienteId).organizacion(organizacion).build();
    }

    private Membresia membresiaDe(RolGestion rolGestion, boolean esTerapeuta) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeutaId))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build();
    }

    private AccesoPaciente accesoDeEquipo() {
        return new AccesoPaciente(paciente, membresiaDe(RolGestion.OWNER, true), false, null);
    }

    private Sesion sesionPrueba(LocalDateTime fechaHora, String objetivos) {
        return Sesion.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .fechaHora(fechaHora)
                .objetivosTrabajados(objetivos)
                .observaciones("Obs")
                .estrategiasYProximosPasos("Estrategia")
                .build();
    }

    // ──────────────────────────────────────────────
    //  OBTENER SESIONES (GESTION_CLINICA)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo (gestión/clínico asignado) → obtiene las sesiones del paciente")
    void obtener_equipoDeGestion_obtieneSesiones() {
        Sesion s1 = sesionPrueba(LocalDateTime.of(2026, 9, 1, 10, 0), "Objetivo A");
        Sesion s2 = sesionPrueba(LocalDateTime.of(2026, 8, 30, 15, 30), "Objetivo B");

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByPacienteId(pacienteId)).willReturn(List.of(s1, s2));

        List<SesionResponseDTO> resultado = sesionService.obtenerSesionesDePaciente(pacienteId, "test@ejemplo.com");

        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(SesionResponseDTO::objetivosTrabajados)
                .containsExactly("Objetivo A", "Objetivo B");
        assertThat(resultado.get(0).pacienteId()).isEqualTo(pacienteId);
        assertThat(resultado.get(0).observaciones()).isEqualTo("Obs");
    }

    @Test
    @DisplayName("PacienteFamiliar (cualquier permiso) → NO obtiene las sesiones (exclusión familiar sin cambios)")
    void obtener_familiar_lanzaExcepcion() {
        Usuario familiar = Usuario.builder().id(UUID.randomUUID()).email("familiar@ejemplo.com").build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() ->
                sesionService.obtenerSesionesDePaciente(pacienteId, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).findByPacienteId(any());
    }

    @Test
    @DisplayName("Miembro sin acceso de equipo → no obtiene sesiones (404 genérico)")
    void obtener_sinAccesoDeEquipo_lanzaExcepcion() {
        Usuario otro = Usuario.builder().id(UUID.randomUUID()).email("otro@ejemplo.com").build();

        given(usuarioRepository.findByEmail("otro@ejemplo.com")).willReturn(Optional.of(otro));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, otro, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() ->
                sesionService.obtenerSesionesDePaciente(pacienteId, "otro@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).findByPacienteId(any());
    }

    @Test
    @DisplayName("Usuario inexistente → lanza excepción")
    void obtener_usuarioInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.obtenerSesionesDePaciente(pacienteId, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    // ──────────────────────────────────────────────
    //  REGISTRAR SESIÓN (GESTION_CLINICA)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → puede registrar una sesión")
    void registrar_equipoDeGestion_puedeRegistrar() {
        SesionRegistroDTO dto = new SesionRegistroDTO(
                LocalDateTime.of(2026, 9, 2, 9, 0),
                "sentado",
                "Trabajar vocabulario",
                "Buena sesión",
                "Continuar con verbos");
        Sesion sesionGuardada = Sesion.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .fechaHora(dto.fechaHora())
                .disposicion(dto.disposicion())
                .objetivosTrabajados(dto.objetivosTrabajados())
                .observaciones(dto.observaciones())
                .estrategiasYProximosPasos(dto.estrategiasYProximosPasos())
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.save(any(Sesion.class))).willReturn(sesionGuardada);

        SesionResponseDTO response = sesionService.registrarSesion(pacienteId, dto, "test@ejemplo.com");

        assertThat(response.objetivosTrabajados()).isEqualTo("Trabajar vocabulario");
        assertThat(response.fechaHora()).isEqualTo(dto.fechaHora());
        assertThat(response.pacienteId()).isEqualTo(pacienteId);
    }

    @Test
    @DisplayName("Registrar sesión con principal inexistente → 404 genérico \"Usuario no encontrado\"")
    void registrar_principalInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        SesionRegistroDTO dto = new SesionRegistroDTO(
                LocalDateTime.of(2026, 9, 2, 9, 0),
                "sentado",
                "Objetivo",
                null,
                null);

        assertThatThrownBy(() -> sesionService.registrarSesion(pacienteId, dto, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(sesionRepository, never()).save(any());
    }

    @Test
    @DisplayName("PacienteFamiliar → NO puede registrar una sesión (exclusión familiar sin cambios)")
    void registrar_familiar_lanzaExcepcion() {
        Usuario familiar = Usuario.builder().id(UUID.randomUUID()).email("familiar@ejemplo.com").build();
        SesionRegistroDTO dto = new SesionRegistroDTO(
                LocalDateTime.of(2026, 9, 2, 9, 0),
                null,
                "Objetivo",
                null,
                null);

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() ->
                sesionService.registrarSesion(pacienteId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  OBTENER SESIÓN POR ID (GESTION_CLINICA)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → obtiene la sesión por id")
    void obtenerSesion_equipoDeGestion_obtieneSesion() {
        UUID sesionId = UUID.randomUUID();
        Sesion sesion = Sesion.builder()
                .id(sesionId)
                .paciente(paciente)
                .fechaHora(LocalDateTime.of(2026, 9, 1, 10, 0))
                .objetivosTrabajados("Objetivo A")
                .observaciones("Obs")
                .estrategiasYProximosPasos("Sigue")
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(sesionId, pacienteId))
                .willReturn(Optional.of(sesion));

        SesionResponseDTO resultado = sesionService.obtenerSesion(pacienteId, sesionId, "test@ejemplo.com");

        assertThat(resultado.id()).isEqualTo(sesionId);
        assertThat(resultado.objetivosTrabajados()).isEqualTo("Objetivo A");
        assertThat(resultado.pacienteId()).isEqualTo(pacienteId);
    }

    @Test
    @DisplayName("Sesión de OTRO paciente → no la obtiene (404 genérico)")
    void obtenerSesion_sesionDeOtroPaciente_lanzaExcepcion() {
        UUID sesionId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(sesionId, pacienteId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.obtenerSesion(pacienteId, sesionId, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no encontrada o no tiene permisos");
    }

    @Test
    @DisplayName("Sin acceso de equipo al paciente → no obtiene la sesión (404 genérico)")
    void obtenerSesion_sinAccesoDeEquipo_lanzaExcepcion() {
        Usuario otro = Usuario.builder().id(UUID.randomUUID()).email("otro@ejemplo.com").build();

        given(usuarioRepository.findByEmail("otro@ejemplo.com")).willReturn(Optional.of(otro));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, otro, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() ->
                sesionService.obtenerSesion(pacienteId, UUID.randomUUID(), "otro@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).findByIdAndPacienteId(any(), any());
    }

    @Test
    @DisplayName("Sesión inexistente → lanza excepción (404 genérico)")
    void obtenerSesion_sesionInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.obtenerSesion(pacienteId, UUID.randomUUID(), "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no encontrada o no tiene permisos");
    }

    // ──────────────────────────────────────────────
    //  ACTUALIZAR SESIÓN (GESTION_CLINICA)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → actualiza la sesión")
    void actualizarSesion_equipoDeGestion_actualiza() {
        UUID sesionId = UUID.randomUUID();
        Sesion sesionExistente = Sesion.builder()
                .id(sesionId)
                .paciente(paciente)
                .fechaHora(LocalDateTime.of(2026, 9, 1, 10, 0))
                .objetivosTrabajados("Antes")
                .build();
        SesionActualizacionDTO dto = new SesionActualizacionDTO(
                LocalDateTime.of(2026, 9, 5, 11, 30),
                "de pie",
                "Después",
                "Nueva obs",
                "Nueva estrategia");
        Sesion sesionActualizada = Sesion.builder()
                .id(sesionId)
                .paciente(paciente)
                .fechaHora(dto.fechaHora())
                .disposicion(dto.disposicion())
                .objetivosTrabajados(dto.objetivosTrabajados())
                .observaciones(dto.observaciones())
                .estrategiasYProximosPasos(dto.estrategiasYProximosPasos())
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(sesionId, pacienteId))
                .willReturn(Optional.of(sesionExistente));
        given(sesionRepository.save(any(Sesion.class))).willReturn(sesionActualizada);

        SesionResponseDTO response = sesionService.actualizarSesion(pacienteId, sesionId, dto, "test@ejemplo.com");

        assertThat(response.objetivosTrabajados()).isEqualTo("Después");
        assertThat(response.fechaHora()).isEqualTo(dto.fechaHora());
        assertThat(response.observaciones()).isEqualTo("Nueva obs");
        verify(sesionRepository).save(sesionExistente);
    }

    @Test
    @DisplayName("Sesión de OTRO paciente → no la actualiza (404 genérico)")
    void actualizarSesion_sesionDeOtroPaciente_lanzaExcepcion() {
        UUID sesionId = UUID.randomUUID();
        SesionActualizacionDTO dto = new SesionActualizacionDTO(
                LocalDateTime.of(2026, 9, 5, 11, 30),
                null,
                "Objetivo",
                null,
                null);

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(sesionId, pacienteId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.actualizarSesion(pacienteId, sesionId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no encontrada o no tiene permisos");

        verify(sesionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin acceso de equipo al paciente → no actualiza la sesión (404 genérico)")
    void actualizarSesion_sinAccesoDeEquipo_lanzaExcepcion() {
        Usuario otro = Usuario.builder().id(UUID.randomUUID()).email("otro@ejemplo.com").build();
        SesionActualizacionDTO dto = new SesionActualizacionDTO(
                LocalDateTime.of(2026, 9, 5, 11, 30),
                null,
                "Objetivo",
                null,
                null);

        given(usuarioRepository.findByEmail("otro@ejemplo.com")).willReturn(Optional.of(otro));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, otro, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() ->
                sesionService.actualizarSesion(pacienteId, UUID.randomUUID(), dto, "otro@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).findByIdAndPacienteId(any(), any());
        verify(sesionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sesión inexistente → no la actualiza (404 genérico)")
    void actualizarSesion_sesionInexistente_lanzaExcepcion() {
        SesionActualizacionDTO dto = new SesionActualizacionDTO(
                LocalDateTime.of(2026, 9, 5, 11, 30),
                null,
                "Objetivo",
                null,
                null);

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.actualizarSesion(pacienteId, UUID.randomUUID(), dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no encontrada o no tiene permisos");

        verify(sesionRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  ELIMINAR SESIÓN (GESTION_CLINICA)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → elimina la sesión")
    void eliminarSesion_equipoDeGestion_elimina() {
        UUID sesionId = UUID.randomUUID();
        Sesion sesion = Sesion.builder()
                .id(sesionId)
                .paciente(paciente)
                .fechaHora(LocalDateTime.of(2026, 9, 1, 10, 0))
                .objetivosTrabajados("Objetivo A")
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(sesionId, pacienteId))
                .willReturn(Optional.of(sesion));

        sesionService.eliminarSesion(pacienteId, sesionId, "test@ejemplo.com");

        verify(sesionRepository).delete(sesion);
    }

    @Test
    @DisplayName("Sesión de OTRO paciente → no la elimina (404 genérico)")
    void eliminarSesion_sesionDeOtroPaciente_lanzaExcepcion() {
        UUID sesionId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(sesionId, pacienteId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.eliminarSesion(pacienteId, sesionId, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no encontrada o no tiene permisos");

        verify(sesionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Sin acceso de equipo al paciente → no elimina la sesión (404 genérico)")
    void eliminarSesion_sinAccesoDeEquipo_lanzaExcepcion() {
        Usuario otro = Usuario.builder().id(UUID.randomUUID()).email("otro@ejemplo.com").build();

        given(usuarioRepository.findByEmail("otro@ejemplo.com")).willReturn(Optional.of(otro));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, otro, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() ->
                sesionService.eliminarSesion(pacienteId, UUID.randomUUID(), "otro@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).findByIdAndPacienteId(any(), any());
        verify(sesionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Sesión inexistente → no la elimina (404 genérico)")
    void eliminarSesion_sesionInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(sesionRepository.findByIdAndPacienteId(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.eliminarSesion(pacienteId, UUID.randomUUID(), "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no encontrada o no tiene permisos");

        verify(sesionRepository, never()).delete(any());
    }
}
