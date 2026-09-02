package com.caa.api.services;

import com.caa.api.dtos.PacienteResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.impl.PacienteServiceImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PacienteServiceImpl — obtenerPaciente (GET /api/pacientes/{id})")
class PacienteServiceImplTest {

    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteFamiliarRepository pacienteFamiliarRepository;

    @InjectMocks private PacienteServiceImpl pacienteService;

    private UUID pacienteId;
    private UUID terapeutaId;
    private UUID familiarId;
    private Usuario terapeuta;
    private Usuario familiar;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        terapeutaId = UUID.randomUUID();
        familiarId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).rol(RolUsuario.TERAPEUTA).build();
        familiar = Usuario.builder().id(familiarId).rol(RolUsuario.FAMILIAR).build();
        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
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

    @Test
    @DisplayName("Terapeuta propietario → obtiene el paciente")
    void terapeutaPropietario_obtienePaciente() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));

        PacienteResponseDTO dto = pacienteService.obtenerPaciente(pacienteId, "terapeuta@ejemplo.com");

        assertThat(dto.id()).isEqualTo(pacienteId);
        assertThat(dto.nombre()).isEqualTo("Nico");
        assertThat(dto.apellido()).isEqualTo("Perez");
        assertThat(dto.fechaNacimiento()).isEqualTo(LocalDate.of(2020, 5, 10));
    }

    @Test
    @DisplayName("Familiar asignado → obtiene el paciente")
    void familiarAsignado_obtienePaciente() {
        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarId))
                .willReturn(Optional.of(vinculo()));

        PacienteResponseDTO dto = pacienteService.obtenerPaciente(pacienteId, "familiar@ejemplo.com");

        assertThat(dto.id()).isEqualTo(pacienteId);
        assertThat(dto.nombre()).isEqualTo("Nico");
        assertThat(dto.apellido()).isEqualTo("Perez");
    }

    @Test
    @DisplayName("Paciente inexistente o ajeno → lanza excepción 404")
    void pacienteInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("terapeuta@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.obtenerPaciente(pacienteId, "terapeuta@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
    }

    @Test
    @DisplayName("Usuario inexistente → lanza excepción")
    void usuarioInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.obtenerPaciente(pacienteId, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no encontrado");
    }
}