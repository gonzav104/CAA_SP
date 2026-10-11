package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.caa.api.dtos.MiembroRolGestionDTO;
import com.caa.api.dtos.MiembroTerapeutaDTO;
import com.caa.api.dtos.OrganizacionActualizacionDTO;
import com.caa.api.dtos.OrganizacionRegistroDTO;
import com.caa.api.dtos.TransferenciaPropiedadDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.impl.OrganizacionServiceImpl;
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

/**
 * Tests unitarios de {@code OrganizacionServiceImpl} (design §12.2, design-part2 §13): CRUD de
 * {@code Organizacion} y gestión de {@code Membresia}. {@code AccesoService} se mockea
 * directamente: su propia matriz de resolución vive en {@code AccesoServiceImplTest} /
 * {@code AccesoServiceExigirAltaPacienteTest}, no se reimplementa aquí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizacionServiceImpl — CRUD de organizaciones y gestión de membresías")
class OrganizacionServiceImplTest {

    @Mock private OrganizacionRepository organizacionRepository;
    @Mock private MembresiaRepository membresiaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private PacienteTerapeutaRepository pacienteTerapeutaRepository;
    @Mock private AccesoService accesoService;

    @InjectMocks private OrganizacionServiceImpl organizacionService;

    private UUID organizacionId;
    private UUID ownerId;
    private UUID otroUsuarioId;
    private String emailOwner;
    private Usuario owner;
    private Usuario otroUsuario;
    private Organizacion organizacion;

    @BeforeEach
    void setUp() {
        organizacionId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        otroUsuarioId = UUID.randomUUID();
        emailOwner = "owner@ejemplo.com";

        owner = Usuario.builder().id(ownerId).email(emailOwner).nombre("Owner").build();
        otroUsuario = Usuario.builder().id(otroUsuarioId).email("otro@ejemplo.com").nombre("Otro").build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").creadoPor(owner).build();
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
    //  crear
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("crear sin esTerapeuta → default true, Membresia OWNER")
    void crear_sinEsTerapeuta_defaultTrue() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.save(any(Organizacion.class))).willReturn(organizacion);

        var response = organizacionService.crear(new OrganizacionRegistroDTO("Consultorio", null), emailOwner);

        assertThat(response.miRolGestion()).isEqualTo(RolGestion.OWNER);
        assertThat(response.miEsTerapeuta()).isTrue();
        verify(membresiaRepository).save(org.mockito.ArgumentMatchers.argThat(m ->
                m.getRolGestion() == RolGestion.OWNER && m.isEsTerapeuta()));
    }

    @Test
    @DisplayName("crear con esTerapeuta=false → Membresia OWNER sin capacidad clínica")
    void crear_conEsTerapeutaFalse() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.save(any(Organizacion.class))).willReturn(organizacion);

        var response = organizacionService.crear(new OrganizacionRegistroDTO("Consultorio", false), emailOwner);

        assertThat(response.miEsTerapeuta()).isFalse();
        verify(membresiaRepository).save(org.mockito.ArgumentMatchers.argThat(m -> !m.isEsTerapeuta()));
    }

    // ──────────────────────────────────────────────
    //  listarMisOrganizaciones / obtener
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listarMisOrganizaciones devuelve rolGestion y esTerapeuta por membresía")
    void listarMisOrganizaciones_devuelveTodas() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(membresiaRepository.findByUsuario_Id(ownerId))
                .willReturn(List.of(membresia(owner, RolGestion.OWNER, true)));

        var result = organizacionService.listarMisOrganizaciones(emailOwner);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).miRolGestion()).isEqualTo(RolGestion.OWNER);
    }

    @Test
    @DisplayName("obtener delega en AccesoService.exigirMembresia")
    void obtener_delegaEnAccesoService() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(accesoService.exigirMembresia(organizacionId, owner))
                .willReturn(membresia(owner, RolGestion.ADMIN, false));

        var result = organizacionService.obtener(organizacionId, emailOwner);

        assertThat(result.miRolGestion()).isEqualTo(RolGestion.ADMIN);
        assertThat(result.miEsTerapeuta()).isFalse();
    }

    // ──────────────────────────────────────────────
    //  renombrar
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("renombrar exige rolGestion OWNER o ADMIN")
    void renombrar_actualizaNombre() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));
        given(organizacionRepository.save(any(Organizacion.class))).willAnswer(inv -> inv.getArgument(0));

        var result = organizacionService.renombrar(
                organizacionId, new OrganizacionActualizacionDTO("Nuevo nombre"), emailOwner);

        assertThat(result.nombre()).isEqualTo("Nuevo nombre");
        assertThat(organizacion.getNombre()).isEqualTo("Nuevo nombre");
    }

    // ──────────────────────────────────────────────
    //  eliminar
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("eliminar sin pacientes → borra la organización")
    void eliminar_sinPacientes_elimina() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));
        given(pacienteRepository.existsByOrganizacion_Id(organizacionId)).willReturn(false);

        organizacionService.eliminar(organizacionId, emailOwner);

        verify(organizacionRepository).deleteById(organizacionId);
    }

    @Test
    @DisplayName("eliminar con pacientes existentes → 409, no borra")
    void eliminar_conPacientes_lanzaConflicto() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));
        given(pacienteRepository.existsByOrganizacion_Id(organizacionId)).willReturn(true);

        assertThatThrownBy(() -> organizacionService.eliminar(organizacionId, emailOwner))
                .isInstanceOf(ConflictoException.class);

        verify(organizacionRepository, never()).deleteById(any());
    }

    // ──────────────────────────────────────────────
    //  cambiarRolGestion
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("cambiarRolGestion propone OWNER → 400 (IllegalArgumentException), sin tocar nada")
    void cambiarRolGestion_proponeOwner_lanzaIllegalArgument() {
        assertThatThrownBy(() -> organizacionService.cambiarRolGestion(
                organizacionId, otroUsuarioId, new MiembroRolGestionDTO(RolGestion.OWNER), emailOwner))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("cambiarRolGestion OWNER válido → actualiza el rolGestion del objetivo")
    void cambiarRolGestion_valido_actualiza() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));
        Membresia objetivo = membresia(otroUsuario, RolGestion.MIEMBRO, false);
        given(membresiaRepository.findById(new MembresiaId(organizacionId, otroUsuarioId)))
                .willReturn(Optional.of(objetivo));
        given(membresiaRepository.save(any(Membresia.class))).willAnswer(inv -> inv.getArgument(0));

        var result = organizacionService.cambiarRolGestion(
                organizacionId, otroUsuarioId, new MiembroRolGestionDTO(RolGestion.ADMIN), emailOwner);

        assertThat(result.rolGestion()).isEqualTo(RolGestion.ADMIN);
    }

    @Test
    @DisplayName("cambiarRolGestion sobre la propia fila del OWNER → 403, sin modificar")
    void cambiarRolGestion_targetEsOwner_lanzaAccesoDenegado() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, ownerId)))
                .willReturn(Optional.of(membresia(owner, RolGestion.OWNER, true)));

        assertThatThrownBy(() -> organizacionService.cambiarRolGestion(
                organizacionId, ownerId, new MiembroRolGestionDTO(RolGestion.ADMIN), emailOwner))
                .isInstanceOf(AccesoDenegadoException.class);

        verify(membresiaRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  cambiarEsTerapeuta
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("cambiarEsTerapeuta ADMIN sobre OWNER → 403")
    void cambiarEsTerapeuta_adminSobreOwner_lanzaAccesoDenegado() {
        given(usuarioRepository.findByEmail("admin@ejemplo.com")).willReturn(Optional.of(otroUsuario));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirRolGestion(organizacionId, otroUsuario, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(otroUsuario, RolGestion.ADMIN, false));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, ownerId)))
                .willReturn(Optional.of(membresia(owner, RolGestion.OWNER, true)));

        assertThatThrownBy(() -> organizacionService.cambiarEsTerapeuta(
                organizacionId, ownerId, new MiembroTerapeutaDTO(false), "admin@ejemplo.com"))
                .isInstanceOf(AccesoDenegadoException.class);

        verify(membresiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEsTerapeuta OWNER sobre sí mismo → permitido")
    void cambiarEsTerapeuta_ownerSobreSiMismo_permitido() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        Membresia ownerMembresia = membresia(owner, RolGestion.OWNER, true);
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(ownerMembresia);
        given(membresiaRepository.findById(new MembresiaId(organizacionId, ownerId)))
                .willReturn(Optional.of(ownerMembresia));
        given(membresiaRepository.save(any(Membresia.class))).willAnswer(inv -> inv.getArgument(0));

        var result = organizacionService.cambiarEsTerapeuta(
                organizacionId, ownerId, new MiembroTerapeutaDTO(false), emailOwner);

        assertThat(result.esTerapeuta()).isFalse();
        verify(pacienteTerapeutaRepository).deleteByOrganizacionIdAndUsuario_Id(organizacionId, ownerId);
    }

    @Test
    @DisplayName("cambiarEsTerapeuta a true NO dispara el borrado masivo de asignaciones")
    void cambiarEsTerapeuta_aTrue_noBorraAsignaciones() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));
        Membresia objetivo = membresia(otroUsuario, RolGestion.MIEMBRO, false);
        given(membresiaRepository.findById(new MembresiaId(organizacionId, otroUsuarioId)))
                .willReturn(Optional.of(objetivo));
        given(membresiaRepository.save(any(Membresia.class))).willAnswer(inv -> inv.getArgument(0));

        organizacionService.cambiarEsTerapeuta(
                organizacionId, otroUsuarioId, new MiembroTerapeutaDTO(true), emailOwner);

        verify(pacienteTerapeutaRepository, never()).deleteByOrganizacionIdAndUsuario_Id(any(), any());
    }

    // ──────────────────────────────────────────────
    //  eliminarMiembro
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("eliminarMiembro: intento sobre la fila OWNER → 409 (transferir primero)")
    void eliminarMiembro_targetEsOwner_lanzaConflicto() {
        given(usuarioRepository.findByEmail("admin@ejemplo.com")).willReturn(Optional.of(otroUsuario));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirMembresia(organizacionId, otroUsuario))
                .willReturn(membresia(otroUsuario, RolGestion.ADMIN, false));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, ownerId)))
                .willReturn(Optional.of(membresia(owner, RolGestion.OWNER, true)));

        assertThatThrownBy(() -> organizacionService.eliminarMiembro(organizacionId, ownerId, "admin@ejemplo.com"))
                .isInstanceOf(ConflictoException.class);

        verify(membresiaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("eliminarMiembro: ADMIN intenta eliminar otro ADMIN → 403")
    void eliminarMiembro_adminEliminaAdmin_lanzaAccesoDenegado() {
        String emailAdmin = "admin@ejemplo.com";
        Usuario admin = Usuario.builder().id(UUID.randomUUID()).email(emailAdmin).build();
        UUID otroAdminId = UUID.randomUUID();
        Usuario otroAdmin = Usuario.builder().id(otroAdminId).build();

        given(usuarioRepository.findByEmail(emailAdmin)).willReturn(Optional.of(admin));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirMembresia(organizacionId, admin))
                .willReturn(membresia(admin, RolGestion.ADMIN, false));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, otroAdminId)))
                .willReturn(Optional.of(membresia(otroAdmin, RolGestion.ADMIN, false)));

        assertThatThrownBy(() -> organizacionService.eliminarMiembro(organizacionId, otroAdminId, emailAdmin))
                .isInstanceOf(AccesoDenegadoException.class);

        verify(membresiaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("eliminarMiembro: ADMIN elimina a un MIEMBRO → OK, borra asignaciones primero")
    void eliminarMiembro_adminEliminaMiembro_elimina() {
        String emailAdmin = "admin@ejemplo.com";
        Usuario admin = Usuario.builder().id(UUID.randomUUID()).email(emailAdmin).build();

        given(usuarioRepository.findByEmail(emailAdmin)).willReturn(Optional.of(admin));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirMembresia(organizacionId, admin))
                .willReturn(membresia(admin, RolGestion.ADMIN, false));
        Membresia objetivo = membresia(otroUsuario, RolGestion.MIEMBRO, true);
        given(membresiaRepository.findById(new MembresiaId(organizacionId, otroUsuarioId)))
                .willReturn(Optional.of(objetivo));

        organizacionService.eliminarMiembro(organizacionId, otroUsuarioId, emailAdmin);

        verify(pacienteTerapeutaRepository).deleteByOrganizacionIdAndUsuario_Id(organizacionId, otroUsuarioId);
        verify(membresiaRepository).delete(objetivo);
    }

    @Test
    @DisplayName("eliminarMiembro: salida voluntaria de un MIEMBRO no-OWNER → OK")
    void eliminarMiembro_salidaVoluntaria_elimina() {
        given(usuarioRepository.findByEmail("otro@ejemplo.com")).willReturn(Optional.of(otroUsuario));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        Membresia propia = membresia(otroUsuario, RolGestion.MIEMBRO, true);
        given(accesoService.exigirMembresia(organizacionId, otroUsuario)).willReturn(propia);
        given(membresiaRepository.findById(new MembresiaId(organizacionId, otroUsuarioId)))
                .willReturn(Optional.of(propia));

        organizacionService.eliminarMiembro(organizacionId, otroUsuarioId, "otro@ejemplo.com");

        verify(membresiaRepository).delete(propia);
    }

    // ──────────────────────────────────────────────
    //  transferirPropiedad
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("transferirPropiedad: degrada al OWNER actual y promueve al destino, sin tocar esTerapeuta")
    void transferirPropiedad_valido_actualizaAmbasMembresias() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        Membresia ownerMembresia = membresia(owner, RolGestion.OWNER, true);
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER)))
                .willReturn(ownerMembresia);
        Membresia destinoMembresia = membresia(otroUsuario, RolGestion.ADMIN, false);
        given(membresiaRepository.findById(new MembresiaId(organizacionId, otroUsuarioId)))
                .willReturn(Optional.of(destinoMembresia));

        organizacionService.transferirPropiedad(
                organizacionId, new TransferenciaPropiedadDTO(otroUsuarioId), emailOwner);

        assertThat(ownerMembresia.getRolGestion()).isEqualTo(RolGestion.ADMIN);
        assertThat(ownerMembresia.isEsTerapeuta()).isTrue();
        assertThat(destinoMembresia.getRolGestion()).isEqualTo(RolGestion.OWNER);
        assertThat(destinoMembresia.isEsTerapeuta()).isFalse();
    }

    @Test
    @DisplayName("transferirPropiedad: destino no es miembro de la organización → 404")
    void transferirPropiedad_destinoNoEsMiembro_lanza404() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, otroUsuarioId)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> organizacionService.transferirPropiedad(
                organizacionId, new TransferenciaPropiedadDTO(otroUsuarioId), emailOwner))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("transferirPropiedad: destino es el propio OWNER actual → 409")
    void transferirPropiedad_destinoEsElMismo_lanzaConflicto() {
        given(usuarioRepository.findByEmail(emailOwner)).willReturn(Optional.of(owner));
        given(organizacionRepository.findConBloqueoById(organizacionId)).willReturn(Optional.of(organizacion));
        given(accesoService.exigirRolGestion(organizacionId, owner, Set.of(RolGestion.OWNER)))
                .willReturn(membresia(owner, RolGestion.OWNER, true));

        assertThatThrownBy(() -> organizacionService.transferirPropiedad(
                organizacionId, new TransferenciaPropiedadDTO(ownerId), emailOwner))
                .isInstanceOf(ConflictoException.class);

        verify(membresiaRepository, never()).save(any());
    }
}
