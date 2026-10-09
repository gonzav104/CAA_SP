package com.caa.api.services;

import com.caa.api.dtos.AceptarInvitacionDTO;
import com.caa.api.dtos.AceptarInvitacionResponseDTO;
import com.caa.api.dtos.InvitacionOrganizacionRegistroDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.Invitacion;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.TipoInvitacion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.InvitacionRepository;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.InvitacionServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios de {@code InvitacionServiceImpl} (design §9, spec {@code invitations}; tareas
 * 6.4-6.7, 7.1-7.3). {@code AccesoService} se mockea directamente: su matriz de resolución vive
 * en {@code AccesoServiceImplTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvitacionServiceImpl — creación, revocación y aceptación")
class InvitacionServiceImplTest {

    @Mock private InvitacionRepository invitacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OrganizacionRepository organizacionRepository;
    @Mock private MembresiaRepository membresiaRepository;
    @Mock private PacienteFamiliarRepository pacienteFamiliarRepository;
    @Mock private AccesoService accesoService;
    @Mock private TokenSeguro tokenSeguro;
    @Mock private EmailService emailService;

    @InjectMocks private InvitacionServiceImpl invitacionService;

    private UUID organizacionId;
    private UUID ownerId;
    private String emailOwner;
    private Usuario owner;
    private Organizacion organizacion;

    @BeforeEach
    void setUp() {
        organizacionId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        emailOwner = "owner@ejemplo.com";

        owner = Usuario.builder().id(ownerId).email(emailOwner).nombre("Owner").build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").creadoPor(owner).build();

        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
    }

    private Membresia membresia(RolGestion rolGestion) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, ownerId))
                .organizacion(organizacion)
                .usuario(owner)
                .rolGestion(rolGestion)
                .esTerapeuta(true)
                .build();
    }

    // ──────────────────────────────────────────────
    //  CREAR ORGANIZACIÓN
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER puede proponer ADMIN con esTerapeuta libre")
    void crearOrganizacion_ownerProponeAdmin_ok() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.OWNER));
        given(tokenSeguro.generar()).willReturn("token-crudo");
        given(tokenSeguro.hash("token-crudo")).willReturn("hash-xyz");
        given(invitacionRepository.findByOrganizacion_IdAndEmailAndEstado(any(), any(), any()))
                .willReturn(Optional.empty());
        given(invitacionRepository.saveAndFlush(any(Invitacion.class))).willAnswer(inv -> inv.getArgument(0));

        InvitacionOrganizacionRegistroDTO dto = new InvitacionOrganizacionRegistroDTO(
                "nuevo@ejemplo.com", RolGestion.ADMIN, false);

        InvitacionResponseDTO respuesta = invitacionService.crearOrganizacion(organizacionId, dto, emailOwner);

        assertThat(respuesta.tipo()).isEqualTo(TipoInvitacion.ORGANIZACION);
        assertThat(respuesta.rolGestionPropuesto()).isEqualTo(RolGestion.ADMIN);
        assertThat(respuesta.esTerapeutaPropuesto()).isFalse();
        assertThat(respuesta.estado()).isEqualTo(EstadoInvitacion.PENDIENTE);

        verify(emailService).enviarInvitacionOrganizacion("nuevo@ejemplo.com", organizacion, RolGestion.ADMIN, false, "token-crudo");
    }

    @Test
    @DisplayName("Proponer rolGestion=OWNER siempre se rechaza, incluso para el propio OWNER")
    void crearOrganizacion_proponerOwner_rechazado() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.OWNER));

        InvitacionOrganizacionRegistroDTO dto = new InvitacionOrganizacionRegistroDTO(
                "nuevo@ejemplo.com", RolGestion.OWNER, true);

        assertThatThrownBy(() -> invitacionService.crearOrganizacion(organizacionId, dto, emailOwner))
                .isInstanceOf(IllegalArgumentException.class);

        verify(invitacionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ADMIN solo puede proponer MIEMBRO — proponer ADMIN se rechaza (403)")
    void crearOrganizacion_adminProponeAdmin_rechazado() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.ADMIN));

        InvitacionOrganizacionRegistroDTO dto = new InvitacionOrganizacionRegistroDTO(
                "nuevo@ejemplo.com", RolGestion.ADMIN, true);

        assertThatThrownBy(() -> invitacionService.crearOrganizacion(organizacionId, dto, emailOwner))
                .isInstanceOf(AccesoDenegadoException.class);

        verify(invitacionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ADMIN puede proponer MIEMBRO con esTerapeuta=true")
    void crearOrganizacion_adminProponeMiembro_ok() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.ADMIN));
        given(tokenSeguro.generar()).willReturn("token-crudo");
        given(tokenSeguro.hash(any())).willReturn("hash-xyz");
        given(invitacionRepository.findByOrganizacion_IdAndEmailAndEstado(any(), any(), any()))
                .willReturn(Optional.empty());
        given(invitacionRepository.saveAndFlush(any(Invitacion.class))).willAnswer(inv -> inv.getArgument(0));

        InvitacionOrganizacionRegistroDTO dto = new InvitacionOrganizacionRegistroDTO(
                "nuevo@ejemplo.com", RolGestion.MIEMBRO, true);

        InvitacionResponseDTO respuesta = invitacionService.crearOrganizacion(organizacionId, dto, emailOwner);

        assertThat(respuesta.rolGestionPropuesto()).isEqualTo(RolGestion.MIEMBRO);
        assertThat(respuesta.esTerapeutaPropuesto()).isTrue();
    }

    @Test
    @DisplayName("Invitar a alguien que ya es miembro → ConflictoException (409)")
    void crearOrganizacion_yaEsMiembro_lanzaConflicto() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.OWNER));
        Usuario existente = Usuario.builder().id(UUID.randomUUID()).email("ya@ejemplo.com").build();
        given(usuarioRepository.findByEmail("ya@ejemplo.com")).willReturn(Optional.of(existente));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, existente.getId())))
                .willReturn(Optional.of(membresia(RolGestion.MIEMBRO)));

        InvitacionOrganizacionRegistroDTO dto = new InvitacionOrganizacionRegistroDTO(
                "ya@ejemplo.com", RolGestion.MIEMBRO, true);

        assertThatThrownBy(() -> invitacionService.crearOrganizacion(organizacionId, dto, emailOwner))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("No se puede invitar a este usuario");

        verify(invitacionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Resend: invitación PENDIENTE existente se reutiliza y rota el token")
    void crearOrganizacion_reenvio_reutilizaFilaYRotaToken() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.OWNER));
        Invitacion existente = Invitacion.builder()
                .id(UUID.randomUUID())
                .tipo(TipoInvitacion.ORGANIZACION)
                .organizacion(organizacion)
                .email("nuevo@ejemplo.com")
                .rolGestionPropuesto(RolGestion.MIEMBRO)
                .esTerapeutaPropuesto(false)
                .tokenHash("hash-vieja")
                .estado(EstadoInvitacion.PENDIENTE)
                .expiraEn(LocalDateTime.now().plusDays(1))
                .invitadoPor(owner)
                .build();
        given(invitacionRepository.findByOrganizacion_IdAndEmailAndEstado(
                organizacionId, "nuevo@ejemplo.com", EstadoInvitacion.PENDIENTE))
                .willReturn(Optional.of(existente));
        given(tokenSeguro.generar()).willReturn("token-nuevo");
        given(tokenSeguro.hash("token-nuevo")).willReturn("hash-nueva");
        given(invitacionRepository.saveAndFlush(any(Invitacion.class))).willAnswer(inv -> inv.getArgument(0));

        InvitacionOrganizacionRegistroDTO dto = new InvitacionOrganizacionRegistroDTO(
                "nuevo@ejemplo.com", RolGestion.ADMIN, true);

        invitacionService.crearOrganizacion(organizacionId, dto, emailOwner);

        ArgumentCaptor<Invitacion> captor = ArgumentCaptor.forClass(Invitacion.class);
        verify(invitacionRepository).saveAndFlush(captor.capture());
        Invitacion guardada = captor.getValue();
        assertThat(guardada.getId()).isEqualTo(existente.getId());
        assertThat(guardada.getTokenHash()).isEqualTo("hash-nueva");
        assertThat(guardada.getRolGestionPropuesto()).isEqualTo(RolGestion.ADMIN);
        assertThat(guardada.getEsTerapeutaPropuesto()).isTrue();
    }

    // ──────────────────────────────────────────────
    //  REVOCAR ORGANIZACIÓN
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Revocar una invitación PENDIENTE la transiciona a REVOCADA")
    void revocarOrganizacion_pendiente_laRevoca() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.OWNER));
        UUID invitacionId = UUID.randomUUID();
        Invitacion invitacion = Invitacion.builder()
                .id(invitacionId).tipo(TipoInvitacion.ORGANIZACION).organizacion(organizacion)
                .estado(EstadoInvitacion.PENDIENTE).build();
        given(invitacionRepository.findById(invitacionId)).willReturn(Optional.of(invitacion));

        invitacionService.revocarOrganizacion(organizacionId, invitacionId, emailOwner);

        ArgumentCaptor<Invitacion> captor = ArgumentCaptor.forClass(Invitacion.class);
        verify(invitacionRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoInvitacion.REVOCADA);
        assertThat(captor.getValue().getResueltaEn()).isNotNull();
    }

    @Test
    @DisplayName("Revocar una invitación de otra organización → 404 genérico")
    void revocarOrganizacion_deOtraOrganizacion_404() {
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(RolGestion.OWNER));
        UUID invitacionId = UUID.randomUUID();
        Organizacion otraOrg = Organizacion.builder().id(UUID.randomUUID()).nombre("Otra").build();
        Invitacion invitacion = Invitacion.builder()
                .id(invitacionId).tipo(TipoInvitacion.ORGANIZACION).organizacion(otraOrg)
                .estado(EstadoInvitacion.PENDIENTE).build();
        given(invitacionRepository.findById(invitacionId)).willReturn(Optional.of(invitacion));

        assertThatThrownBy(() -> invitacionService.revocarOrganizacion(organizacionId, invitacionId, emailOwner))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(invitacionRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  ACEPTAR
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Token inexistente → error genérico, nada se crea")
    void aceptar_tokenInexistente_errorGenerico() {
        given(tokenSeguro.hash("no-existe")).willReturn("hash-no-existe");
        given(invitacionRepository.findByTokenHash("hash-no-existe")).willReturn(Optional.empty());

        assertThatThrownBy(() -> invitacionService.aceptar(new AceptarInvitacionDTO("no-existe"), emailOwner))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no es válido o ha expirado");

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Email del usuario autenticado no coincide con el de la invitación → error genérico")
    void aceptar_emailNoCoincide_errorGenerico() {
        Invitacion invitacion = Invitacion.builder()
                .id(UUID.randomUUID()).tipo(TipoInvitacion.ORGANIZACION).organizacion(organizacion)
                .email("otro@ejemplo.com").estado(EstadoInvitacion.PENDIENTE)
                .expiraEn(LocalDateTime.now().plusDays(1)).build();
        given(tokenSeguro.hash("token")).willReturn("hash");
        given(invitacionRepository.findByTokenHash("hash")).willReturn(Optional.of(invitacion));

        assertThatThrownBy(() -> invitacionService.aceptar(new AceptarInvitacionDTO("token"), emailOwner))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(invitacionRepository, never()).aceptarSiVigente(any(), any(), any());
    }

    @Test
    @DisplayName("Conditional update devuelve 0 (expirada/revocada/ya aceptada) → error genérico")
    void aceptar_updateCondicionalFalla_errorGenerico() {
        Invitacion invitacion = Invitacion.builder()
                .id(UUID.randomUUID()).tipo(TipoInvitacion.ORGANIZACION).organizacion(organizacion)
                .email(emailOwner).estado(EstadoInvitacion.PENDIENTE)
                .expiraEn(LocalDateTime.now().minusMinutes(1)).build();
        given(tokenSeguro.hash("token")).willReturn("hash");
        given(invitacionRepository.findByTokenHash("hash")).willReturn(Optional.of(invitacion));
        given(invitacionRepository.aceptarSiVigente(eq(invitacion.getId()), eq(owner), any())).willReturn(0);

        assertThatThrownBy(() -> invitacionService.aceptar(new AceptarInvitacionDTO("token"), emailOwner))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aceptar invitación ORGANIZACION vigente → crea la Membresia con los valores propuestos")
    void aceptar_organizacionVigente_creaMembresia() {
        Invitacion invitacion = Invitacion.builder()
                .id(UUID.randomUUID()).tipo(TipoInvitacion.ORGANIZACION).organizacion(organizacion)
                .email(emailOwner).rolGestionPropuesto(RolGestion.ADMIN).esTerapeutaPropuesto(false)
                .estado(EstadoInvitacion.PENDIENTE).expiraEn(LocalDateTime.now().plusDays(1)).build();
        given(tokenSeguro.hash("token")).willReturn("hash");
        given(invitacionRepository.findByTokenHash("hash")).willReturn(Optional.of(invitacion));
        given(invitacionRepository.aceptarSiVigente(eq(invitacion.getId()), eq(owner), any())).willReturn(1);
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, ownerId))).willReturn(Optional.empty());

        AceptarInvitacionResponseDTO respuesta = invitacionService.aceptar(new AceptarInvitacionDTO("token"), emailOwner);

        assertThat(respuesta.tipo()).isEqualTo(TipoInvitacion.ORGANIZACION);
        assertThat(respuesta.organizacionId()).isEqualTo(organizacionId);
        assertThat(respuesta.pacienteId()).isNull();

        ArgumentCaptor<Membresia> captor = ArgumentCaptor.forClass(Membresia.class);
        verify(membresiaRepository).save(captor.capture());
        assertThat(captor.getValue().getRolGestion()).isEqualTo(RolGestion.ADMIN);
        assertThat(captor.getValue().isEsTerapeuta()).isFalse();
    }

    @Test
    @DisplayName("Aceptar cuando la Membresia ya existe (carrera) → no inserta una segunda fila")
    void aceptar_membresiaYaExiste_noDuplica() {
        Invitacion invitacion = Invitacion.builder()
                .id(UUID.randomUUID()).tipo(TipoInvitacion.ORGANIZACION).organizacion(organizacion)
                .email(emailOwner).rolGestionPropuesto(RolGestion.MIEMBRO).esTerapeutaPropuesto(true)
                .estado(EstadoInvitacion.PENDIENTE).expiraEn(LocalDateTime.now().plusDays(1)).build();
        given(tokenSeguro.hash("token")).willReturn("hash");
        given(invitacionRepository.findByTokenHash("hash")).willReturn(Optional.of(invitacion));
        given(invitacionRepository.aceptarSiVigente(eq(invitacion.getId()), eq(owner), any())).willReturn(1);
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, ownerId)))
                .willReturn(Optional.of(membresia(RolGestion.OWNER)));

        invitacionService.aceptar(new AceptarInvitacionDTO("token"), emailOwner);

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aceptar invitación PACIENTE_FAMILIAR vigente → crea el PacienteFamiliar con el permiso propuesto")
    void aceptar_familiarVigente_creaVinculo() {
        Paciente paciente = Paciente.builder().id(UUID.randomUUID()).nombre("Nico").apellido("Perez").build();
        Invitacion invitacion = Invitacion.builder()
                .id(UUID.randomUUID()).tipo(TipoInvitacion.PACIENTE_FAMILIAR).paciente(paciente)
                .email(emailOwner).permisoPropuesto(PermisoColaborador.EDICION_LIMITADA)
                .estado(EstadoInvitacion.PENDIENTE).expiraEn(LocalDateTime.now().plusDays(1)).build();
        given(tokenSeguro.hash("token")).willReturn("hash");
        given(invitacionRepository.findByTokenHash("hash")).willReturn(Optional.of(invitacion));
        given(invitacionRepository.aceptarSiVigente(eq(invitacion.getId()), eq(owner), any())).willReturn(1);
        given(pacienteFamiliarRepository.findById(new PacienteFamiliarId(paciente.getId(), ownerId)))
                .willReturn(Optional.empty());

        AceptarInvitacionResponseDTO respuesta = invitacionService.aceptar(new AceptarInvitacionDTO("token"), emailOwner);

        assertThat(respuesta.tipo()).isEqualTo(TipoInvitacion.PACIENTE_FAMILIAR);
        assertThat(respuesta.pacienteId()).isEqualTo(paciente.getId());
        assertThat(respuesta.organizacionId()).isNull();

        ArgumentCaptor<PacienteFamiliar> captor = ArgumentCaptor.forClass(PacienteFamiliar.class);
        verify(pacienteFamiliarRepository).save(captor.capture());
        assertThat(captor.getValue().getPermiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);
        verify(organizacionRepository, never()).findConBloqueoById(any());
    }

    // ──────────────────────────────────────────────
    //  CREAR FAMILIAR
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("crearFamiliar exige Capacidad.GESTION_CLINICA y delega la autorización en AccesoService")
    void crearFamiliar_exigeCapacidadGestionClinica() {
        UUID pacienteId = UUID.randomUUID();
        Paciente paciente = Paciente.builder().id(pacienteId).nombre("Nico").apellido("Perez").build();
        given(accesoService.exigirCapacidad(pacienteId, owner, Capacidad.GESTION_CLINICA))
                .willReturn(new AccesoPaciente(paciente, membresia(RolGestion.OWNER), false, null));
        given(invitacionRepository.findByPaciente_IdAndEmailAndEstado(any(), any(), any()))
                .willReturn(Optional.empty());
        given(tokenSeguro.generar()).willReturn("token-fam");
        given(tokenSeguro.hash("token-fam")).willReturn("hash-fam");
        given(invitacionRepository.saveAndFlush(any(Invitacion.class))).willAnswer(inv -> inv.getArgument(0));

        InvitacionResponseDTO respuesta = invitacionService.crearFamiliar(
                pacienteId, "familiar@ejemplo.com", PermisoColaborador.LECTURA, emailOwner);

        assertThat(respuesta.tipo()).isEqualTo(TipoInvitacion.PACIENTE_FAMILIAR);
        assertThat(respuesta.permisoPropuesto()).isEqualTo(PermisoColaborador.LECTURA);
        verify(emailService).enviarInvitacionFamiliar("familiar@ejemplo.com", paciente, PermisoColaborador.LECTURA, "token-fam");
    }

    @Test
    @DisplayName("crearFamiliar sin Capacidad.GESTION_CLINICA → la excepción de AccesoService se propaga")
    void crearFamiliar_sinCapacidad_propagaExcepcion() {
        UUID pacienteId = UUID.randomUUID();
        given(accesoService.exigirCapacidad(pacienteId, owner, Capacidad.GESTION_CLINICA))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        assertThatThrownBy(() -> invitacionService.crearFamiliar(
                pacienteId, "familiar@ejemplo.com", PermisoColaborador.LECTURA, emailOwner))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(invitacionRepository, never()).saveAndFlush(any());
    }
}
