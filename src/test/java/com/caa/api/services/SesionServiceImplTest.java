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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SesionServiceImpl — Tests unitarios")
class SesionServiceImplTest {

    @Mock private SesionRepository sesionRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteService pacienteService;

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
    //  OBTENER SESIONES (lectura)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Terapeuta propietario → obtiene las sesiones del paciente")
    void obtener_terapeutaPropietario_obtieneSesiones() {
        Sesion s1 = sesionPrueba(LocalDateTime.of(2026, 9, 1, 10, 0), "Objetivo A");
        Sesion s2 = sesionPrueba(LocalDateTime.of(2026, 8, 30, 15, 30), "Objetivo B");

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteService.pacienteLegibleParaUsuario(pacienteId, terapeuta)).willReturn(paciente);
        given(sesionRepository.findByPacienteId(pacienteId)).willReturn(List.of(s1, s2));

        List<SesionResponseDTO> resultado = sesionService.obtenerSesionesDePaciente(pacienteId, "test@ejemplo.com");

        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(SesionResponseDTO::objetivosTrabajados)
                .containsExactly("Objetivo A", "Objetivo B");
        assertThat(resultado.get(0).pacienteId()).isEqualTo(pacienteId);
        assertThat(resultado.get(0).observaciones()).isEqualTo("Obs");
    }

    @Test
    @DisplayName("Familiar asignado al paciente → obtiene las sesiones (solo lectura)")
    void obtener_familiarAsignado_obtieneSesiones() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();
        Sesion s1 = sesionPrueba(LocalDateTime.of(2026, 9, 1, 10, 0), "Objetivo A");

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(pacienteService.pacienteLegibleParaUsuario(pacienteId, familiar)).willReturn(paciente);
        given(sesionRepository.findByPacienteId(pacienteId)).willReturn(List.of(s1));

        List<SesionResponseDTO> resultado = sesionService.obtenerSesionesDePaciente(pacienteId, "familiar@ejemplo.com");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).objetivosTrabajados()).isEqualTo("Objetivo A");
    }

    @Test
    @DisplayName("Usuario sin acceso al paciente → lanza 404 (excepción del dominio)")
    void obtener_usuarioSinAcceso_lanzaExcepcion() {
        UUID otroUsuarioId = UUID.randomUUID();
        Usuario ajeno = Usuario.builder()
                .id(otroUsuarioId)
                .email("ajeno@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail("ajeno@ejemplo.com")).willReturn(Optional.of(ajeno));
        given(pacienteService.pacienteLegibleParaUsuario(pacienteId, ajeno))
                .willThrow(new RecursoNoEncontradoException("No tiene acceso a este paciente"));

        assertThatThrownBy(() ->
                sesionService.obtenerSesionesDePaciente(pacienteId, "ajeno@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("No tiene acceso");

        org.mockito.Mockito.verify(sesionRepository, org.mockito.Mockito.never()).findByPacienteId(any());
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

        org.mockito.Mockito.verify(sesionRepository, org.mockito.Mockito.never()).save(any());
    }
}
