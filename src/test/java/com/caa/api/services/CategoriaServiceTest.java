package com.caa.api.services;

import com.caa.api.dtos.CategoriaRegistroDTO;
import com.caa.api.dtos.CategoriaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.Paciente;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.impl.CategoriaServiceImpl;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoriaService — Tests unitarios")
class CategoriaServiceTest {

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private CartillaRepository cartillaRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private CategoriaServiceImpl categoriaService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID cartillaId;
    private Usuario terapeuta;
    private Paciente paciente;
    private Cartilla cartilla;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        cartillaId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").rol(RolUsuario.TERAPEUTA).build();
        paciente = Paciente.builder().id(pacienteId).terapeuta(terapeuta).build();
        cartilla = Cartilla.builder().id(cartillaId).paciente(paciente).nombre("Cartilla").esPrincipal(false).build();
    }

    @Test
    @DisplayName("Crear categoría en cartilla ajena al paciente → lanza excepción (ownership transitivo)")
    void crear_cartillaDeOtroPaciente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.empty());

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Comida", "#E0E0E0", null);

        assertThatThrownBy(() ->
                categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Cartilla no encontrada");

        org.mockito.Mockito.verify(categoriaRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Orden auto-asignado: si va null, toma max+1 de la cartilla")
    void crear_ordenAutomatico_esMaxMasUno() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));

        Categoria existente = Categoria.builder().id(UUID.randomUUID()).cartilla(cartilla).orden(3).build();
        given(categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId)).willReturn(List.of(existente));

        Categoria guardada = Categoria.builder()
                .id(UUID.randomUUID()).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").orden(4).build();
        given(categoriaRepository.save(any(Categoria.class))).willReturn(guardada);

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Acciones", "#00FF00", null);

        CategoriaResponseDTO response = categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.orden()).isEqualTo(4);
    }

    @Test
    @DisplayName("Crear categoría con orden explícito → respeta ese orden")
    void crear_ordenExplicito_respetaOrden() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));

        Categoria guardada = Categoria.builder()
                .id(UUID.randomUUID()).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").orden(9).build();
        given(categoriaRepository.save(any(Categoria.class))).willReturn(guardada);

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Acciones", "#00FF00", 9);

        CategoriaResponseDTO response = categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.orden()).isEqualTo(9);
    }
}
