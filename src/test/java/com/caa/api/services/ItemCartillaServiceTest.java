package com.caa.api.services;

import com.caa.api.dtos.ItemCartillaActualizacionDTO;
import com.caa.api.dtos.ItemCartillaRegistroDTO;
import com.caa.api.dtos.ItemCartillaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Paciente;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.impl.ItemCartillaServiceImpl;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemCartillaService — Tests unitarios (XOR + ownership por creador)")
class ItemCartillaServiceTest {

    @Mock private ItemCartillaRepository itemCartillaRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private CartillaRepository cartillaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PictogramaGlobalRepository pictogramaGlobalRepository;
    @Mock private PictogramaCustomRepository pictogramaCustomRepository;
    @Mock private PacienteService pacienteService;

    @InjectMocks private ItemCartillaServiceImpl itemCartillaService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID cartillaId;
    private UUID categoriaId;
    private UUID itemId;
    private Usuario terapeuta;
    private Paciente paciente;
    private Cartilla cartilla;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        cartillaId = UUID.randomUUID();
        categoriaId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").rol(RolUsuario.TERAPEUTA).build();
        paciente = Paciente.builder().id(pacienteId).terapeuta(terapeuta).build();
        cartilla = Cartilla.builder()
                .id(cartillaId).paciente(paciente).creador(terapeuta).nombre("Cartilla").esPrincipal(false).build();
        categoria = Categoria.builder().id(categoriaId).cartilla(cartilla).nombre("Acciones").build();
    }

    // ──────────────────────────────────────────────
    //  OWNERSHIP: gate de creador de la cartilla
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("No-creador de la cartilla → 404 genérico ANTES de la regla XOR")
    void crear_noCreador_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        // Aunque el dto viole XOR (ambos null), el gate de creador gana
        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, null, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(itemCartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Creador de la cartilla → puede actualizar el item")
    void actualizar_creadorDeCartilla_puedeActualizar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
        ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria).build();
        given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)).willReturn(Optional.of(item));

        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

        ItemCartilla guardado = ItemCartilla.builder().id(itemId).categoria(categoria).textoHablado("Hola")
                .ordenVisual(1).recursoGlobal(global).build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaActualizacionDTO dto = new ItemCartillaActualizacionDTO("Hola", 1, globalId, null, null);

        ItemCartillaResponseDTO response =
                itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId, itemId, dto, "test@ejemplo.com");

        assertThat(response.textoHablado()).isEqualTo("Hola");
        assertThat(response.recursoGlobalId()).isEqualTo(globalId);
    }

    @Test
    @DisplayName("Creador de la cartilla → puede eliminar el item")
    void eliminar_creadorDeCartilla_puedeEliminar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
        ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria).build();
        given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)).willReturn(Optional.of(item));

        itemCartillaService.eliminarItem(pacienteId, cartillaId, categoriaId, itemId, "test@ejemplo.com");

        verify(itemCartillaRepository).delete(item);
    }

    @Test
    @DisplayName("No-creador de la cartilla → NO puede eliminar el item (404 genérico)")
    void eliminar_noCreador_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                itemCartillaService.eliminarItem(pacienteId, cartillaId, categoriaId, itemId, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(itemCartillaRepository, never()).delete(any());
    }

    // ──────────────────────────────────────────────
    //  REGLA XOR: exactamente un recurso
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Item sin recurso (ninguno) → lanza excepción por XOR")
    void crear_sinRecurso_lanzaXor() {
        prepararCreador();

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, null, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente un recurso");

        verify(itemCartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Item con AMBOS recursos → lanza excepción por XOR")
    void crear_ambosRecursos_lanzaXor() {
        prepararCreador();

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, UUID.randomUUID(), UUID.randomUUID(), null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente un recurso");
    }

    @Test
    @DisplayName("Item con recursoCustom de OTRO paciente → lanza excepción (ownership del recurso)")
    void crear_customDeOtroPaciente_lanzaExcepcion() {
        prepararCreador();

        UUID customId = UUID.randomUUID();
        UUID otroPacienteId = UUID.randomUUID();
        PictogramaCustom custom = PictogramaCustom.builder()
                .id(customId)
                .paciente(Paciente.builder().id(otroPacienteId).build()) // otro paciente
                .build();

        given(pictogramaCustomRepository.findById(customId)).willReturn(Optional.of(custom));

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, customId, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece al paciente");
    }

    // ──────────────────────────────────────────────
    //  HAPPY PATH
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Item con recursoGlobal válido → crea correctamente")
    void crear_conRecursoGlobal_creaCorrectamente() {
        prepararCreador();

        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

        ItemCartilla guardado = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Hola")
                .ordenVisual(1)
                .recursoGlobal(global)
                .build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, globalId, null, null);

        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response).isNotNull();
        assertThat(response.recursoGlobalId()).isEqualTo(globalId);
        assertThat(response.recursoCustomId()).isNull();
        assertThat(response.textoHablado()).isEqualTo("Hola");
    }

    @Test
    @DisplayName("Item con recursoCustom del MISMO paciente → crea correctamente")
    void crear_conRecursoCustomPropio_creaCorrectamente() {
        prepararCreador();

        UUID customId = UUID.randomUUID();
        PictogramaCustom custom = PictogramaCustom.builder().id(customId).paciente(paciente).build();
        given(pictogramaCustomRepository.findById(customId)).willReturn(Optional.of(custom));

        ItemCartilla guardado = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Hola")
                .ordenVisual(1)
                .recursoCustom(custom)
                .build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, customId, null);

        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response.recursoCustomId()).isEqualTo(customId);
        assertThat(response.recursoGlobalId()).isNull();
    }

    // ──────────────────────────────────────────────
    //  ES_CORE: binario clínico marcado por el terapeuta
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Crear item con esCore=true → el item se persiste como core")
    void crear_conEsCoreTrue_persisteComoCore() {
        prepararCreador();

        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

        ItemCartilla guardado = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Hola")
                .ordenVisual(1)
                .recursoGlobal(global)
                .esCore(true)
                .build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, globalId, null, true);

        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response.esCore()).isTrue();
        verify(itemCartillaRepository).save(argThat(ItemCartilla::isEsCore));
    }

    @Test
    @DisplayName("Crear item sin esCore (null) → default false (comportamiento actual / backfill)")
    void crear_sinEsCore_defaultFalse() {
        prepararCreador();

        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

        ItemCartilla guardado = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Hola")
                .ordenVisual(1)
                .recursoGlobal(global)
                .esCore(false)
                .build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, globalId, null, null);

        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response.esCore()).isFalse();
        verify(itemCartillaRepository).save(argThat(i -> !i.isEsCore()));
    }

    @Test
    @DisplayName("Actualizar con esCore=false explícito → desmarca el item (update parcial)")
    void actualizar_conEsCoreFalse_desmarcaCore() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
        ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria).esCore(true).build();
        given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)).willReturn(Optional.of(item));

        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

        ItemCartilla guardado = ItemCartilla.builder().id(itemId).categoria(categoria).esCore(false).build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaActualizacionDTO dto = new ItemCartillaActualizacionDTO("Hola", 1, globalId, null, false);

        itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId, itemId, dto, "test@ejemplo.com");

        verify(itemCartillaRepository).save(argThat(i -> !i.isEsCore()));
    }

    @Test
    @DisplayName("Actualizar sin campo esCore (null) → PRESERVA el valor existente")
    void actualizar_sinEsCore_preservaValor() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
        ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria).esCore(true).build();
        given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)).willReturn(Optional.of(item));

        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

        ItemCartilla guardado = ItemCartilla.builder().id(itemId).categoria(categoria).esCore(true).build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaActualizacionDTO dto = new ItemCartillaActualizacionDTO("Hola", 1, globalId, null, null);

        itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId, itemId, dto, "test@ejemplo.com");

        verify(itemCartillaRepository).save(argThat(ItemCartilla::isEsCore));
    }

    private void prepararCreador() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
    }
}