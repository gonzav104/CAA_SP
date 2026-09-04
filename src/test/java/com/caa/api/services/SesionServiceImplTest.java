package com.caa.api.services;

import com.caa.api.dtos.SesionRegistroDTO;
import com.caa.api.dtos.SesionResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Sesion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.SesionRepository;
import com.caa.api.repositories.UsuarioRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SesionServiceImpl — Tests unitarios")
class SesionServiceImplTest {

    @Mock private SesionRepository sesionRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private SesionServiceImpl sesionService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private Usuario terapeuta;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        terapeuta = Usuario.builder()
                .id(terapeutaId)
                .email("test@ejemplo.com")
                .rol(RolUsuario.TERAPEUTA)
                .build();
        paciente = Paciente.builder().id(pacienteId).terapeuta(terapeuta).build();
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
    //  OBTENER SESIONES (lectura terapeuta-only)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Terapeuta propietario → obtiene las sesiones del paciente")
    void obtener_terapeutaPropietario_obtieneSesiones() {
        Sesion s1 = sesionPrueba(LocalDateTime.of(2026, 9, 1, 10, 0), "Objetivo A");
        Sesion s2 = sesionPrueba(LocalDateTime.of(2026, 8, 30, 15, 30), "Objetivo B");

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(sesionRepository.findByPacienteId(pacienteId)).willReturn(List.of(s1, s2));

        List<SesionResponseDTO> resultado = sesionService.obtenerSesionesDePaciente(pacienteId, "test@ejemplo.com");

        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(SesionResponseDTO::objetivosTrabajados)
                .containsExactly("Objetivo A", "Objetivo B");
        assertThat(resultado.get(0).pacienteId()).isEqualTo(pacienteId);
        assertThat(resultado.get(0).observaciones()).isEqualTo("Obs");
    }

    @Test
    @DisplayName("Familiar asignado al paciente → NO obtiene las sesiones (terapeuta-only, 404)")
    void obtener_familiar_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, familiarId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.obtenerSesionesDePaciente(pacienteId, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).findByPacienteId(any());
    }

    @Test
    @DisplayName("Terapeuta de OTRO paciente → no obtiene sesiones (404 genérico)")
    void obtener_terapeutaDeOtroPaciente_lanzaExcepcion() {
        UUID otroTerapeutaId = UUID.randomUUID();
        Usuario otroTerapeuta = Usuario.builder()
                .id(otroTerapeutaId)
                .email("otro@ejemplo.com")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        given(usuarioRepository.findByEmail("otro@ejemplo.com")).willReturn(Optional.of(otroTerapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, otroTerapeutaId))
                .willReturn(Optional.empty());

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
    //  REGISTRAR SESIÓN (solo terapeuta)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Terapeuta propietario → puede registrar una sesión")
    void registrar_terapeutaPropietario_puedeRegistrar() {
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
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(sesionRepository.save(any(Sesion.class))).willReturn(sesionGuardada);

        SesionResponseDTO response = sesionService.registrarSesion(pacienteId, dto, "test@ejemplo.com");

        assertThat(response.objetivosTrabajados()).isEqualTo("Trabajar vocabulario");
        assertThat(response.fechaHora()).isEqualTo(dto.fechaHora());
        assertThat(response.pacienteId()).isEqualTo(pacienteId);
    }

    @Test
    @DisplayName("Familiar asignado → NO puede registrar una sesión (terapeuta-only)")
    void registrar_familiar_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();
        SesionRegistroDTO dto = new SesionRegistroDTO(
                LocalDateTime.of(2026, 9, 2, 9, 0),
                null,
                "Objetivo",
                null,
                null);

        // El POST usa findByIdAndTerapeutaId (terapeuta-only): el familiar no es terapeuta,
        // así que la búsqueda por terapeuta no encuentra el paciente → 404.
        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, familiarId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                sesionService.registrarSesion(pacienteId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(sesionRepository, never()).save(any());
    }
}