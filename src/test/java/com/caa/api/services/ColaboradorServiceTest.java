package com.caa.api.services;

import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.TipoInvitacion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.Capacidad;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Migrado (spec {@code patient-collaborators} MODIFICADA): autorización vía
 * {@code AccesoService.Capacidad.GESTION_CLINICA} en vez de {@code pacienteDelTerapeuta}; agregar
 * un colaborador ahora delega en {@link InvitacionService#crearFamiliar} en vez de vincular
 * directamente — ya no exige que el invitado tenga cuenta con {@code rol == FAMILIAR}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ColaboradorService — Tests unitarios")
class ColaboradorServiceTest {

    @Mock private PacienteFamiliarRepository pacienteFamiliarRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AccesoService accesoService;
    @Mock private InvitacionService invitacionService;

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
                .build();
        paciente = Paciente.builder()
                .id(pacienteId)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build();
        familiar = Usuario.builder()
                .id(familiarId)
                .email("mama@ejemplo.com")
                .nombre("Mama de Nico")
                .build();
    }

    private void givenAutenticado() {
        given(usuarioRepository.findByEmail(emailTerapeuta)).willReturn(Optional.of(terapeuta));
    }

    // ──────────────────────────────────────────────
    //  VINCULAR (POST) — ahora crea una invitación
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Vincular delega íntegramente en InvitacionService.crearFamiliar")
    void vincular_delegaEnInvitacionService() {
        ColaboradorRegistroDTO dto = new ColaboradorRegistroDTO("nuevo@ejemplo.com", PermisoColaborador.LECTURA);
        InvitacionResponseDTO esperado = new InvitacionResponseDTO(
                UUID.randomUUID(), TipoInvitacion.PACIENTE_FAMILIAR, "nuevo@ejemplo.com",
                null, null, PermisoColaborador.LECTURA, EstadoInvitacion.PENDIENTE,
                LocalDateTime.now().plusDays(7));
        given(invitacionService.crearFamiliar(pacienteId, "nuevo@ejemplo.com", PermisoColaborador.LECTURA, emailTerapeuta))
                .willReturn(esperado);

        InvitacionResponseDTO response = colaboradorService.vincularColaborador(pacienteId, dto, emailTerapeuta);

        assertThat(response).isEqualTo(esperado);
        verify(invitacionService).crearFamiliar(pacienteId, "nuevo@ejemplo.com", PermisoColaborador.LECTURA, emailTerapeuta);
        verify(accesoService, never()).exigirCapacidad(any(), any(), any());
    }

    // ──────────────────────────────────────────────
    //  LISTAR (GET)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Listar sin Capacidad.GESTION_CLINICA sobre el paciente → excepción propagada, no se consulta la lista")
    void listar_sinCapacidad_lanzaExcepcion() {
        givenAutenticado();
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> colaboradorService.obtenerColaboradores(pacienteId, emailTerapeuta))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(pacienteFamiliarRepository, never()).findByPaciente_Id(any());
    }

    @Test
    @DisplayName("Listar colaboradores con Capacidad.GESTION_CLINICA → devuelve lista")
    void listar_correcto_devuelveLista() {
        givenAutenticado();
        PacienteFamiliar v1 = vinculo();
        PacienteFamiliar v2 = PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, UUID.randomUUID()))
                .paciente(paciente)
                .usuario(Usuario.builder().id(UUID.randomUUID()).nombre("Papa").email("papa@ejemplo.com").build())
                .permiso(PermisoColaborador.LECTURA)
                .build();
        given(pacienteFamiliarRepository.findByPaciente_Id(pacienteId)).willReturn(List.of(v1, v2));

        List<ColaboradorResponseDTO> resultado = colaboradorService.obtenerColaboradores(pacienteId, emailTerapeuta);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).email()).isEqualTo("mama@ejemplo.com");
        assertThat(resultado.get(1).email()).isEqualTo("papa@ejemplo.com");
        verify(accesoService).exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA);
    }

    // ──────────────────────────────────────────────
    //  ACTUALIZAR PERMISO (PUT)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Cambiar permiso de colaborador que no existe → 404 genérico \"Usuario no encontrado\"")
    void actualizar_noEsColaborador_lanzaExcepcion() {
        givenAutenticado();
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
        givenAutenticado();
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
        verify(accesoService).exigirCapacidad(eq(pacienteId), eq(terapeuta), eq(Capacidad.GESTION_CLINICA));
    }

    // ──────────────────────────────────────────────
    //  REVOCAR (DELETE)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Revocar un colaborador que no existe → 404 genérico \"Usuario no encontrado\"")
    void revocar_noEsColaborador_lanzaExcepcion() {
        givenAutenticado();
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
        givenAutenticado();
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
