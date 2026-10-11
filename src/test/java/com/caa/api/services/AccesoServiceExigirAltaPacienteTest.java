package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.services.impl.AccesoServiceImpl;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tarea 2.3/2.4: {@code exigirAltaPaciente}, {@code exigirMembresia} y {@code exigirRolGestion}
 * (design-part2 §11.1, design part 1 §10.3). Cubre la fila completa de la tabla de alta de
 * pacientes de design part 1 §10.3: OWNER/ADMIN permitido sin importar esTerapeuta; MIEMBRO
 * permitido solo con esTerapeuta=true; MIEMBRO con esTerapeuta=false → 403; no-miembro → 404.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccesoServiceImpl — exigirAltaPaciente / exigirMembresia / exigirRolGestion")
class AccesoServiceExigirAltaPacienteTest {

    @Mock private MembresiaRepository membresiaRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private PacienteTerapeutaRepository pacienteTerapeutaRepository;
    @Mock private PacienteFamiliarRepository pacienteFamiliarRepository;

    @InjectMocks private AccesoServiceImpl accesoService;

    private UUID organizacionId;
    private UUID usuarioId;
    private Organizacion organizacion;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        organizacionId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        usuario = Usuario.builder().id(usuarioId).build();
    }

    private Membresia membresia(RolGestion rolGestion, boolean esTerapeuta) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, usuarioId))
                .organizacion(organizacion)
                .usuario(usuario)
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build();
    }

    static Stream<Arguments> matrizAltaPaciente() {
        return Stream.of(
                // rolGestion, esTerapeuta, permitido
                Arguments.of(RolGestion.OWNER, true, true),
                Arguments.of(RolGestion.OWNER, false, true),
                Arguments.of(RolGestion.ADMIN, true, true),
                Arguments.of(RolGestion.ADMIN, false, true),
                Arguments.of(RolGestion.MIEMBRO, true, true),
                Arguments.of(RolGestion.MIEMBRO, false, false)
        );
    }

    @ParameterizedTest(name = "{0} esTerapeuta={1} → permitido={2}")
    @MethodSource("matrizAltaPaciente")
    @DisplayName("Matriz de alta de pacientes (design part 1 §10.3)")
    void exigirAltaPaciente_matriz(RolGestion rolGestion, boolean esTerapeuta, boolean permitido) {
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.of(membresia(rolGestion, esTerapeuta)));

        if (permitido) {
            Membresia resultado = accesoService.exigirAltaPaciente(organizacionId, usuario);
            assertThat(resultado.getRolGestion()).isEqualTo(rolGestion);
            assertThat(resultado.isEsTerapeuta()).isEqualTo(esTerapeuta);
        } else {
            assertThatThrownBy(() -> accesoService.exigirAltaPaciente(organizacionId, usuario))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
    }

    @Test
    @DisplayName("No-miembro de la organización → 404 genérico, nunca 403")
    void exigirAltaPaciente_noMiembro_lanza404() {
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> accesoService.exigirAltaPaciente(organizacionId, usuario))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("exigirMembresia: miembro existente → devuelve la Membresia")
    void exigirMembresia_miembro_devuelveMembresia() {
        Membresia m = membresia(RolGestion.MIEMBRO, false);
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.of(m));

        assertThat(accesoService.exigirMembresia(organizacionId, usuario)).isEqualTo(m);
    }

    @Test
    @DisplayName("exigirMembresia: no-miembro → 404 genérico")
    void exigirMembresia_noMiembro_lanza404() {
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> accesoService.exigirMembresia(organizacionId, usuario))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("exigirRolGestion: OWNER dentro de {OWNER, ADMIN} → permitido")
    void exigirRolGestion_ownerPermitido() {
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.of(membresia(RolGestion.OWNER, false)));

        Membresia resultado = accesoService.exigirRolGestion(
                organizacionId, usuario, Set.of(RolGestion.OWNER, RolGestion.ADMIN));

        assertThat(resultado.getRolGestion()).isEqualTo(RolGestion.OWNER);
    }

    @Test
    @DisplayName("exigirRolGestion: MIEMBRO fuera de {OWNER, ADMIN} → 403")
    void exigirRolGestion_miembroFueraDeConjunto_lanza403() {
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.of(membresia(RolGestion.MIEMBRO, true)));

        assertThatThrownBy(() -> accesoService.exigirRolGestion(
                organizacionId, usuario, Set.of(RolGestion.OWNER, RolGestion.ADMIN)))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("exigirRolGestion: no-miembro → 404, nunca 403")
    void exigirRolGestion_noMiembro_lanza404() {
        given(membresiaRepository.findById(new MembresiaId(organizacionId, usuarioId)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> accesoService.exigirRolGestion(
                organizacionId, usuario, Set.of(RolGestion.OWNER)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
