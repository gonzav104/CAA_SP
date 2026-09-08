package com.caa.api.services;

import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.exceptions.ConflictoException;
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
import com.caa.api.services.impl.ColaboradorServiceImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("ColaboradorService — Tests unitarios")
class ColaboradorServiceTest {

    @Mock private PacienteFamiliarRepository pacienteFamiliarRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmailService emailService;

    @InjectMocks private ColaboradorServiceImpl colaboradorService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID familiarId;
    private String emailTerapeuta;
    private Usuario terapeuta;
    private Paciente paciente;
    private Usuario familiar;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        familiarId = UUID.randomUUID();
        emailTerapeuta = "terapeuta@ejemplo.com";

        terapeuta = Usuario.builder()
                .id(terapeutaId)
                .email(emailTerapeuta)
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();
        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build();
        familiar = Usuario.builder()
                .id(familiarId)
                .email("mama@ejemplo.com")
                .nombre("Mama de Nico")
                .rol(RolUsuario.FAMILIAR)
                .build();
    }

    private void givenOwnership() {
        given(usuarioRepository.findByEmail(emailTerapeuta)).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
    }

    // ──────────────────────────────────────────────
    //  VINCULAR (POST)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Vincular sobre paciente ajeno al terapeuta → RecursoNoEncontradoException")
    void vincular_pacienteAjeno_lanzaExcepcion() {
        given(usuarioRepository.findByEmail(emailTerapeuta)).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        ColaboradorRegistroDTO dto = new ColaboradorRegistroDTO("mama@ejemplo.com", PermisoColaborador.LECTURA);

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(pacienteId, dto, emailTerapeuta))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(pacienteFamiliarRepository, never()).save(any());
        verify(emailService, never()).enviarInvitacionColaborador(any(), any(), any());
    }

    @Test
    @DisplayName("Vincular con email de usuario inexistente → 404 genérico \"Usuario no encontrado\"")
    void vincular_usuarioNoExiste_lanzaExcepcion() {
        givenOwnership();
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        ColaboradorRegistroDTO dto = new ColaboradorRegistroDTO("nadie@ejemplo.com", PermisoColaborador.LECTURA);

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(pacienteId, dto, emailTerapeuta))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(pacienteFamiliarRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vincular con cuenta rol TERAPEUTA → 404 genérico (indistinguible de inexistente)")
    void vincular_usuarioEsTerapeuta_lanzaExcepcion() {
        givenOwnership();
        Usuario otroTerapeuta = Usuario.builder()
                .id(UUID.randomUUID())
                .email("otro@ejemplo.com")
                .nombre("Otro Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();
        given(usuarioRepository.findByEmail("otro@ejemplo.com")).willReturn(Optional.of(otroTerapeuta));

        ColaboradorRegistroDTO dto = new ColaboradorRegistroDTO("otro@ejemplo.com", PermisoColaborador.LECTURA);

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(pacienteId, dto, emailTerapeuta))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(pacienteFamiliarRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vincular con terapeuta autenticado inexistente → 404 genérico \"Usuario no encontrado\" (helper :111)")
    void vincular_terapeutaInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail(emailTerapeuta)).willReturn(Optional.empty());

        ColaboradorRegistroDTO dto = new ColaboradorRegistroDTO("mama@ejemplo.com", PermisoColaborador.LECTURA);

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(pacienteId, dto, emailTerapeuta))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(pacienteFamiliarRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vincular a un usuario que ya es colaborador → ConflictoException (409, texto neutro)")
    void vincular_yaEsColaborador_lanzaConflicto() {
        givenOwnership();
        given(usuarioRepository.findByEmail("mama@ejemplo.com")).willReturn(Optional.of(familiar));
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarId))
                .willReturn(Optional.of(vinculo()));

        ColaboradorRegistroDTO dto = new ColaboradorRegistroDTO("mama@ejemplo.com", PermisoColaborador.LECTURA);

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(pacienteId, dto, emailTerapeuta))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("No se puede vincular este usuario");

        verify(pacienteFamiliarRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vincular correctamente → devuelve DTO del colaborador")
    void vincular_correcto_devuelveDTO() {
        givenOwnership();
        given(usuarioRepository.findByEmail("mama@ejemplo.com")).willReturn(Optional.of(familiar));
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarId))
                .willReturn(Optional.empty());

        PacienteFamiliar guardado = vinculo();
        guardado.setPermiso(PermisoColaborador.EDICION_LIMITADA);
        guardado.setVinculadoEn(LocalDateTime.of(2026, 9, 1, 12, 0));
        given(pacienteFamiliarRepository.saveAndFlush(any(PacienteFamiliar.class))).willReturn(guardado);

        ColaboradorRegistroDTO dto = new ColaboradorRegistroDTO("mama@ejemplo.com", PermisoColaborador.EDICION_LIMITADA);
        ColaboradorResponseDTO response = colaboradorService.vincularColaborador(pacienteId, dto, emailTerapeuta);

        assertThat(response).isNotNull();
        assertThat(response.usuarioId()).isEqualTo(familiarId);
        assertThat(response.nombre()).isEqualTo("Mama de Nico");
        assertThat(response.email()).isEqualTo("mama@ejemplo.com");
        assertThat(response.permiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);
        assertThat(response.vinculadoEn()).isEqualTo(guardado.getVinculadoEn());

        ArgumentCaptor<PacienteFamiliar> captor = ArgumentCaptor.forClass(PacienteFamiliar.class);
        verify(pacienteFamiliarRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPermiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);

        // La invitación se envía al colaborador con el permiso del vínculo
        verify(emailService).enviarInvitacionColaborador(familiar, paciente, PermisoColaborador.EDICION_LIMITADA);
    }

    // ──────────────────────────────────────────────
    //  LISTAR (GET)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Listar colaboradores de paciente propio → devuelve lista")
    void listar_correcto_devuelveLista() {
        givenOwnership();
        PacienteFamiliar v1 = vinculo();
        PacienteFamiliar v2 = PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, UUID.randomUUID()))
                .paciente(paciente)
                .usuario(Usuario.builder().id(UUID.randomUUID()).nombre("Papa").email("papa@ejemplo.com")
                        .rol(RolUsuario.FAMILIAR).build())
                .permiso(PermisoColaborador.LECTURA)
                .build();
        given(pacienteFamiliarRepository.findByPaciente_Id(pacienteId)).willReturn(List.of(v1, v2));

        List<ColaboradorResponseDTO> resultado = colaboradorService.obtenerColaboradores(pacienteId, emailTerapeuta);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).email()).isEqualTo("mama@ejemplo.com");
        assertThat(resultado.get(1).email()).isEqualTo("papa@ejemplo.com");
    }

    // ──────────────────────────────────────────────
    //  ACTUALIZAR PERMISO (PUT)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Cambiar permiso de colaborador que no es del paciente → 404 genérico \"Usuario no encontrado\"")
    void actualizar_noEsColaborador_lanzaExcepcion() {
        givenOwnership();
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarId))
                .willReturn(Optional.empty());

        ColaboradorActualizacionDTO dto = new ColaboradorActualizacionDTO(PermisoColaborador.LECTURA);

        assertThatThrownBy(() -> colaboradorService.actualizarPermiso(pacienteId, familiarId, dto, emailTerapeuta))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(pacienteFamiliarRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cambiar permiso correctamente → devuelve DTO con nuevo permiso")
    void actualizar_correcto_devuelveDTO() {
        givenOwnership();
        PacienteFamiliar vinculo = vinculo();
        vinculo.setPermiso(PermisoColaborador.LECTURA);
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarId))
                .willReturn(Optional.of(vinculo));

        PacienteFamiliar actualizado = vinculo();
        actualizado.setPermiso(PermisoColaborador.EDICION_LIMITADA);
        given(pacienteFamiliarRepository.save(any(PacienteFamiliar.class))).willReturn(actualizado);

        ColaboradorActualizacionDTO dto = new ColaboradorActualizacionDTO(PermisoColaborador.EDICION_LIMITADA);
        ColaboradorResponseDTO response = colaboradorService.actualizarPermiso(pacienteId, familiarId, dto, emailTerapeuta);

        assertThat(response.permiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);

        ArgumentCaptor<PacienteFamiliar> captor = ArgumentCaptor.forClass(PacienteFamiliar.class);
        verify(pacienteFamiliarRepository).save(captor.capture());
        assertThat(captor.getValue().getPermiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);
    }

    // ──────────────────────────────────────────────
    //  REVOCAR (DELETE)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Revocar un colaborador que no es del paciente → 404 genérico \"Usuario no encontrado\"")
    void revocar_noEsColaborador_lanzaExcepcion() {
        givenOwnership();
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> colaboradorService.revocarColaborador(pacienteId, familiarId, emailTerapeuta))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(pacienteFamiliarRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Revocar colaborador correctamente → elimina el vínculo")
    void revocar_correcto_elimina() {
        givenOwnership();
        PacienteFamiliar vinculo = vinculo();
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarId))
                .willReturn(Optional.of(vinculo));

        colaboradorService.revocarColaborador(pacienteId, familiarId, emailTerapeuta);

        verify(pacienteFamiliarRepository).delete(vinculo);
    }

    // ──────────────────────────────────────────────
    //  Helper
    // ──────────────────────────────────────────────

    private PacienteFamiliar vinculo() {
        return PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, familiarId))
                .paciente(paciente)
                .usuario(familiar)
                .permiso(PermisoColaborador.LECTURA)
                .build();
    }
}
