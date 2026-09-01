package com.caa.api.config;

import com.caa.api.dtos.ColaboradorActualizacionDTO;
import com.caa.api.dtos.ColaboradorRegistroDTO;
import com.caa.api.dtos.ColaboradorResponseDTO;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Paciente;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.ColaboradorService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestcol;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret"
})
@Transactional
@DisplayName("ColaboradorService — Integración de punta a punta contra H2")
class ColaboradorServiceIntegrationTest {

    @Autowired private ColaboradorService colaboradorService;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Usuario terapeuta;
    private Paciente paciente;
    private Usuario familiar;

    @BeforeEach
    void setUp() {
        terapeuta = usuarioRepository.save(Usuario.builder()
                .email("terapeuta@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build());

        paciente = pacienteRepository.save(Paciente.builder()
                .terapeuta(terapeuta)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build());

        familiar = usuarioRepository.save(Usuario.builder()
                .email("mama@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Mama de Nico")
                .rol(RolUsuario.FAMILIAR)
                .build());
    }

    @Test
    @DisplayName("Ciclo completo: vincular, listar, cambiar permiso y revocar (punta a punta)")
    void cicloCompletoDeColaboradores() {
        // Vincular
        ColaboradorRegistroDTO registro = new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.LECTURA);
        ColaboradorResponseDTO creado = colaboradorService.vincularColaborador(
                paciente.getId(), registro, terapeuta.getEmail());

        assertThat(creado.usuarioId()).isEqualTo(familiar.getId());
        assertThat(creado.nombre()).isEqualTo("Mama de Nico");
        assertThat(creado.email()).isEqualTo(familiar.getEmail());
        assertThat(creado.permiso()).isEqualTo(PermisoColaborador.LECTURA);
        assertThat(creado.vinculadoEn()).isNotNull();

        // Listar
        List<ColaboradorResponseDTO> lista = colaboradorService.obtenerColaboradores(
                paciente.getId(), terapeuta.getEmail());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).usuarioId()).isEqualTo(familiar.getId());

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
    @DisplayName("Vincular un usuario que ya es colaborador → ConflictoException (409) real")
    void vincularDuplicadoLanzaConflicto() {
        colaboradorService.vincularColaborador(
                paciente.getId(), new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.LECTURA),
                terapeuta.getEmail());

        assertThatThrownBy(() -> colaboradorService.vincularColaborador(
                paciente.getId(), new ColaboradorRegistroDTO(familiar.getEmail(), PermisoColaborador.EDICION_LIMITADA),
                terapeuta.getEmail()))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya es colaborador");
    }

    @Test
    @DisplayName("Vincular sobre paciente ajeno al terapeuta → RecursoNoEncontradoException real")
    void vincularPacienteAjenoLanzaExcepcion() {
        // Otro terapeuta con otro paciente
        Usuario otroTerapeuta = usuarioRepository.save(Usuario.builder()
                .email("otro@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Otro")
                .rol(RolUsuario.TERAPEUTA)
                .build());
        Paciente pacienteAjeno = pacienteRepository.save(Paciente.builder()
                .terapeuta(otroTerapeuta)
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
    @DisplayName("Vincular a un usuario TERAPEUTA → 400 (IllegalArgumentException) real")
    void vincularTerapeutaLanzaExcepcion() {
        assertThatThrownBy(() -> colaboradorService.vincularColaborador(
                paciente.getId(),
                new ColaboradorRegistroDTO(terapeuta.getEmail(), PermisoColaborador.LECTURA),
                terapeuta.getEmail()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo se pueden vincular usuarios con rol FAMILIAR");
    }
}
