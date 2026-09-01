package com.caa.api.services;

import com.caa.api.dtos.ItemCartillaRegistroDTO;
import com.caa.api.dtos.ItemCartillaResponseDTO;
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
import com.caa.api.repositories.PacienteRepository;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemCartillaService — Tests unitarios (XOR + ownership)")
class ItemCartillaServiceTest {

    @Mock private ItemCartillaRepository itemCartillaRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private CartillaRepository cartillaRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PictogramaGlobalRepository pictogramaGlobalRepository;
    @Mock private PictogramaCustomRepository pictogramaCustomRepository;

    @InjectMocks private ItemCartillaServiceImpl itemCartillaService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID cartillaId;
    private UUID categoriaId;
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
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").rol(RolUsuario.TERAPEUTA).build();
        paciente = Paciente.builder().id(pacienteId).terapeuta(terapeuta).build();
        cartilla = Cartilla.builder().id(cartillaId).paciente(paciente).nombre("Cartilla").esPrincipal(false).build();
        categoria = Categoria.builder().id(categoriaId).cartilla(cartilla).nombre("Acciones").build();
    }

    // ──────────────────────────────────────────────
    //  REGLA XOR: exactamente un recurso
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Item sin recurso (ninguno) → lanza excepción por XOR")
    void crear_sinRecurso_lanzaXor() {
        prepararPermisos();

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente un recurso");

        org.mockito.Mockito.verify(itemCartillaRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Item con AMBOS recursos → lanza excepción por XOR")
    void crear_ambosRecursos_lanzaXor() {
        prepararPermisos();

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente un recurso");
    }

    @Test
    @DisplayName("Item con recursoCustom de OTRO paciente → lanza excepción (ownership del recurso)")
    void crear_customDeOtroPaciente_lanzaExcepcion() {
        prepararPermisos();

        UUID customId = UUID.randomUUID();
        UUID otroPacienteId = UUID.randomUUID();
        PictogramaCustom custom = PictogramaCustom.builder()
                .id(customId)
                .paciente(Paciente.builder().id(otroPacienteId).build()) // otro paciente
                .build();

        given(pictogramaCustomRepository.findById(customId)).willReturn(Optional.of(custom));

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, customId);

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
        prepararPermisos();

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

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, globalId, null);

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
        prepararPermisos();

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

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, customId);

        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response.recursoCustomId()).isEqualTo(customId);
        assertThat(response.recursoGlobalId()).isNull();
    }

    private void prepararPermisos() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
    }
}
