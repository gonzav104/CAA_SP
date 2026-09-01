package com.caa.api.services;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Paciente;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.impl.CartillaServiceImpl;
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
@DisplayName("CartillaService — Tests unitarios")
class CartillaServiceTest {

    @Mock private CartillaRepository cartillaRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteService pacienteService;

    @InjectMocks private CartillaServiceImpl cartillaService;

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

    @Test
    @DisplayName("Crear cartilla sobre paciente ajeno al terapeuta → lanza excepción (ownership)")
    void crear_pacienteAjeno_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false);

        assertThatThrownBy(() -> cartillaService.crearCartilla(pacienteId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        org.mockito.Mockito.verify(cartillaRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Terapeuta inexistente → lanza excepción")
    void crear_terapeutaInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false);

        assertThatThrownBy(() -> cartillaService.crearCartilla(pacienteId, dto, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Terapeuta no encontrado");
    }

    @Test
    @DisplayName("Actualizar cartilla que no pertenece al paciente → lanza excepción (ownership anidado)")
    void actualizar_cartillaDeOtroPaciente_lanzaExcepcion() {
        UUID cartillaId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nuevo nombre", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
    }

    @Test
    @DisplayName("Crear cartilla correcto → devuelve DTO con pacienteId")
    void crear_correcto_devuelveDTO() {
        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", true);

        Cartilla cartilla = Cartilla.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .nombre("Cartilla A")
                .esPrincipal(true)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartilla);

        CartillaResponseDTO response = cartillaService.crearCartilla(pacienteId, dto, "test@ejemplo.com");

        assertThat(response).isNotNull();
        assertThat(response.nombre()).isEqualTo("Cartilla A");
        assertThat(response.pacienteId()).isEqualTo(pacienteId);
        assertThat(response.esPrincipal()).isTrue();
    }

    @Test
    @DisplayName("Obtener cartillas de paciente propio → devuelve lista ordenada")
    void obtener_correcto_devuelveLista() {
        Cartilla c1 = Cartilla.builder().id(UUID.randomUUID()).paciente(paciente).nombre("B").esPrincipal(false).build();
        Cartilla c2 = Cartilla.builder().id(UUID.randomUUID()).paciente(paciente).nombre("A").esPrincipal(true).build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteService.pacienteLegibleParaUsuario(eq(pacienteId), eq(terapeuta))).willReturn(paciente);
        given(cartillaRepository.findByPacienteId(pacienteId)).willReturn(List.of(c1, c2));

        List<CartillaResponseDTO> resultado = cartillaService.obtenerCartillasDePaciente(pacienteId, "test@ejemplo.com");

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).nombre()).isEqualTo("B");
        assertThat(resultado.get(1).nombre()).isEqualTo("A");
    }
}
