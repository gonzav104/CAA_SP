package com.caa.api.services;

import com.caa.api.dtos.CategoriaActualizacionDTO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoriaService — Tests unitarios")
class CategoriaServiceTest {

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private CartillaRepository cartillaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteService pacienteService;

    @InjectMocks private CategoriaServiceImpl categoriaService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID cartillaId;
    private UUID categoriaId;
    private Usuario terapeuta;
    private Cartilla cartilla;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        cartillaId = UUID.randomUUID();
        categoriaId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").rol(RolUsuario.TERAPEUTA).build();
        Paciente paciente = Paciente.builder().id(pacienteId).terapeuta(terapeuta).build();
        cartilla = Cartilla.builder()
                .id(cartillaId).paciente(paciente).creador(terapeuta).nombre("Cartilla").esPrincipal(false).build();
        categoria = Categoria.builder()
                .id(categoriaId).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").orden(1).build();
    }

    // ──────────────────────────────────────────────
    //  CREAR (gate: creador de la cartilla padre)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Creador de la cartilla → puede crear categoría")
    void crear_creadorDeCartilla_creaSobreSuCartilla() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));

        Categoria guardada = Categoria.builder()
                .id(UUID.randomUUID()).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").orden(0).build();
        given(categoriaRepository.save(any(Categoria.class))).willReturn(guardada);

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Acciones", "#00FF00", null);

        CategoriaResponseDTO response =
                categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Acciones");
        assertThat(response.orden()).isEqualTo(0);
    }

    @Test
    @DisplayName("Terapeuta dueño pero que NO creó la cartilla → 404 genérico")
    void crear_cartillaDeOtroPaciente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Comida", "#E0E0E0", null);

        assertThatThrownBy(() ->
                categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Familiar vinculado pero que NO creó la cartilla → 404 genérico")
    void crear_familiarNoCreador_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId).email("familiar@ejemplo.com").rol(RolUsuario.FAMILIAR).build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, familiarId))
                .willReturn(Optional.empty());

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Comida", "#E0E0E0", null);

        assertThatThrownBy(() ->
                categoriaService.crearCategoria(pacienteId, cartillaId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Orden auto-asignado: si va null, toma max+1 de la cartilla")
    void crear_ordenAutomatico_esMaxMasUno() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));

        Categoria existente = Categoria.builder().id(UUID.randomUUID()).cartilla(cartilla).orden(3).build();
        given(categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId)).willReturn(List.of(existente));

        Categoria guardada = Categoria.builder()
                .id(UUID.randomUUID()).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").orden(4).build();
        given(categoriaRepository.save(any(Categoria.class))).willReturn(guardada);

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Acciones", "#00FF00", null);

        CategoriaResponseDTO response =
                categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.orden()).isEqualTo(4);
    }

    @Test
    @DisplayName("Crear categoría con orden explícito → respeta ese orden")
    void crear_ordenExplicito_respetaOrden() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));

        Categoria guardada = Categoria.builder()
                .id(UUID.randomUUID()).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").orden(9).build();
        given(categoriaRepository.save(any(Categoria.class))).willReturn(guardada);

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Acciones", "#00FF00", 9);

        CategoriaResponseDTO response =
                categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.orden()).isEqualTo(9);
    }

    // ──────────────────────────────────────────────
    //  ACTUALIZAR / ELIMINAR (gate: creador de la cartilla padre)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Creador de la cartilla → puede actualizar la categoría")
    void actualizar_creadorDeCartilla_puedeActualizar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
        given(categoriaRepository.save(any(Categoria.class))).willReturn(categoria);

        CategoriaActualizacionDTO dto = new CategoriaActualizacionDTO("Nuevo", "#FF0000", 2);

        CategoriaResponseDTO response =
                categoriaService.actualizarCategoria(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Nuevo");
        assertThat(response.colorHex()).isEqualTo("#FF0000");
        assertThat(response.orden()).isEqualTo(2);
    }

    @Test
    @DisplayName("Creador de la cartilla → puede eliminar la categoría")
    void eliminar_creadorDeCartilla_puedeEliminar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));

        categoriaService.eliminarCategoria(pacienteId, cartillaId, categoriaId, "test@ejemplo.com");

        verify(categoriaRepository).delete(categoria);
    }

    @Test
    @DisplayName("No-creador de la cartilla → NO puede eliminar la categoría (404 genérico)")
    void eliminar_noCreador_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                categoriaService.eliminarCategoria(pacienteId, cartillaId, categoriaId, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(categoriaRepository, never()).delete(any());
    }
}