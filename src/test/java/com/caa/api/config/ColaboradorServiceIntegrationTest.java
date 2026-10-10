package com.caa.api.config;

import com.caa.api.dtos.AceptarInvitacionDTO;
import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.TipoInvitacion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.ColaboradorService;
import com.caa.api.services.EmailService;
import com.caa.api.services.InvitacionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Migrado (spec {@code patient-collaborators} MODIFICADA): el ciclo ya no vincula directamente —
 * pasa por una invitación {@code PACIENTE_FAMILIAR} (crear → aceptar) antes de poder listar,
 * actualizar o revocar. Autorización vía {@code Capacidad.GESTION_CLINICA} (Membresia OWNER/ADMIN
 * o miembro asignado).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestcol;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@Transactional
@DisplayName("ColaboradorService — Integración de punta a punta contra H2 (vía invitación)")
class ColaboradorServiceIntegrationTest {

    @Autowired private ColaboradorService colaboradorService;
    @Autowired private InvitacionService invitacionService;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private OrganizacionRepository organizacionRepository;
    @Autowired private MembresiaRepository membresiaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private Usuario terapeuta;
    private Paciente paciente;
    private Usuario familiar;

    @BeforeEach
    void setUp() {
        terapeuta = usuarioRepository.save(Usuario.builder()
                .email("terapeuta@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta")
                .build());

        Organizacion organizacion = organizacionRepository.save(Organizacion.builder()
                .nombre("Consultorio de Terapeuta")
                .creadoPor(terapeuta)
                .build());

        membresiaRepository.save(Membresia.builder()
                .id(new MembresiaId(organizacion.getId(), terapeuta.getId()))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build());

        paciente = pacienteRepository.save(Paciente.builder()
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build());

        familiar = usuarioRepository.save(Usuario.builder()
                .email("mama@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Mama de Nico")
                .build());
    }

    private String aceptarComo(String emailDestino, Usuario quienAcepta) {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarInvitacionFamiliar(eq(emailDestino), any(Paciente.class), any(PermisoColaborador.class),
                tokenCaptor.capture());
        String token = tokenCaptor.getValue();

        invitacionService.aceptar(new AceptarInvitacionDTO(token), quienAcepta.getEmail());
        return token;
    }

    @Test
    @DisplayName("Ciclo completo: invitar, aceptar, listar, cambiar permiso y revocar (punta a punta)")
    void cicloCompletoDeColaboradores() {
        // Invitar (OWNER con GESTION_CLINICA) → crea la invitación, NO el vínculo
        ColaboradorRegistroDTO registro = new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.LECTURA);
        InvitacionResponseDTO invitacion = colaboradorService.vincularColaborador(
                paciente.getId(), registro, terapeuta.getEmail());

        assertThat(invitacion.tipo()).isEqualTo(TipoInvitacion.PACIENTE_FAMILIAR);
        assertThat(invitacion.estado()).isEqualTo(EstadoInvitacion.PENDIENTE);
        assertThat(invitacion.permisoPropuesto()).isEqualTo(PermisoColaborador.LECTURA);
        assertThat(colaboradorService.obtenerColaboradores(paciente.getId(), terapeuta.getEmail())).isEmpty();

        // Aceptar (el familiar, autenticado, con email coincidente) → ahora sí crea el vínculo
        aceptarComo(familiar.getEmail(), familiar);

        // Listar
        List<ColaboradorResponseDTO> lista = colaboradorService.obtenerColaboradores(
                paciente.getId(), terapeuta.getEmail());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).usuarioId()).isEqualTo(familiar.getId());
        assertThat(lista.get(0).permiso()).isEqualTo(PermisoColaborador.LECTURA);

        // Cambiar permiso
        ColaboradorActualizacionDTO actualizacion = new ColaboradorActualizacionDTO(PermisoColaborador.EDICION_LIMITADA);
        ColaboradorResponseDTO actualizado = colaboradorService.actualizarPermiso(
                paciente.getId(), familiar.getId(), actualizacion, terapeuta.getEmail());
        assertThat(actualizado.permiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);

        // Revocar
        colaboradorService.revocarColaborador(paciente.getId(), familiar.getId(), terapeuta.getEmail());
        assertThat(colaboradorService.obtenerColaboradores(paciente.getId(), terapeuta.getEmail())).isEmpty();
    }

    @Test
    @DisplayName("Invitar a un email sin cuenta todavía → la invitación se crea igual, sin crear ningún Usuario")
    void invitarEmailSinCuenta_creaInvitacionSinCrearUsuario() {
        ColaboradorRegistroDTO registro = new ColaboradorRegistroDTO("nuevo@test.com", PermisoColaborador.LECTURA);

        InvitacionResponseDTO invitacion = colaboradorService.vincularColaborador(
                paciente.getId(), registro, terapeuta.getEmail());

        assertThat(invitacion.email()).isEqualTo("nuevo@test.com");
        assertThat(usuarioRepository.findByEmail("nuevo@test.com")).isEmpty();
    }

    @Test
    @DisplayName("Invitar a alguien que ya es colaborador → ConflictoException (409, texto neutro) real")
    void invitarDuplicadoLanzaConflicto() {
        colaboradorService.vincularColaborador(
                paciente.getId(), new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.LECTURA),
                terapeuta.getEmail());
        aceptarComo(familiar.getEmail(), familiar);

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(
                paciente.getId(), new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.EDICION_LIMITADA),
                terapeuta.getEmail()))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("No se puede invitar a este usuario");
    }

    @Test
    @DisplayName("Invitar sobre paciente ajeno a la organización del invitador → RecursoNoEncontradoException real")
    void invitarPacienteAjenoLanzaExcepcion() {
        Usuario otroTerapeuta = usuarioRepository.save(Usuario.builder()
                .email("otro@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Otro")
                .build());
        Organizacion otraOrganizacion = organizacionRepository.save(Organizacion.builder()
                .nombre("Otro consultorio")
                .creadoPor(otroTerapeuta)
                .build());
        membresiaRepository.save(Membresia.builder()
                .id(new MembresiaId(otraOrganizacion.getId(), otroTerapeuta.getId()))
                .organizacion(otraOrganizacion)
                .usuario(otroTerapeuta)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build());
        Paciente pacienteAjeno = pacienteRepository.save(Paciente.builder()
                .organizacion(otraOrganizacion)
                .nombre("Ana")
                .apellido("Gomez")
                .fechaNacimiento(LocalDate.of(2018, 1, 1))
                .build());

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(
                pacienteAjeno.getId(), new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.LECTURA),
                terapeuta.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
    }

    @Test
    @DisplayName("Un miembro MIEMBRO sin asignación clínica no puede invitar colaboradores")
    void miembroSinAsignacion_noPuedeInvitar() {
        Usuario miembroNoAsignado = usuarioRepository.save(Usuario.builder()
                .email("miembro@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Miembro")
                .build());
        Organizacion organizacionDelPaciente = paciente.getOrganizacion();
        membresiaRepository.save(Membresia.builder()
                .id(new MembresiaId(organizacionDelPaciente.getId(), miembroNoAsignado.getId()))
                .organizacion(organizacionDelPaciente)
                .usuario(miembroNoAsignado)
                .rolGestion(RolGestion.MIEMBRO)
                .esTerapeuta(true)
                .build());

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(
                paciente.getId(), new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.LECTURA),
                miembroNoAsignado.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
