package com.caa.api.services;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaDetalleResponseDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.dtos.CategoriaDetalleResponseDTO;
import com.caa.api.dtos.ItemDetalleResponseDTO;
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
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.PictogramaGlobalRepository;
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
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ItemCartillaRepository itemCartillaRepository;
    @Mock private PictogramaGlobalRepository pictogramaGlobalRepository;
    @Mock private PictogramaCustomRepository pictogramaCustomRepository;
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
        org.mockito.BDDMockito.willDoNothing()
                .given(pacienteService).verificarEdicionParaUsuario(pacienteId, terapeuta);
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nuevo nombre", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
    }

    @Test
    @DisplayName("Terapeuta propietario → puede actualizar la cartilla")
    void actualizar_terapeutaPropietario_puedeActualizar() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .nombre("Nombre viejo")
                .esPrincipal(false)
                .build();
        Cartilla cartillaActualizada = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .nombre("Nombre nuevo")
                .esPrincipal(true)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        org.mockito.BDDMockito.willDoNothing()
                .given(pacienteService).verificarEdicionParaUsuario(pacienteId, terapeuta);
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartillaActualizada);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", true);

        CartillaResponseDTO response = cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Nombre nuevo");
        assertThat(response.esPrincipal()).isTrue();
    }

    @Test
    @DisplayName("Familiar con EDICION_LIMITADA → puede actualizar la cartilla")
    void actualizar_familiarEdicionLimitada_puedeActualizar() {
        UUID familiarId = UUID.randomUUID();
        UUID cartillaId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .nombre("Nombre viejo")
                .esPrincipal(false)
                .build();
        Cartilla cartillaActualizada = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .nombre("Nombre nuevo")
                .esPrincipal(false)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        org.mockito.BDDMockito.willDoNothing()
                .given(pacienteService).verificarEdicionParaUsuario(pacienteId, familiar);
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartillaActualizada);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false);

        CartillaResponseDTO response = cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "familiar@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Nombre nuevo");
        org.mockito.Mockito.verify(pacienteService).verificarEdicionParaUsuario(pacienteId, familiar);
    }

    @Test
    @DisplayName("Familiar con LECTURA → NO puede actualizar la cartilla")
    void actualizar_familiarLectura_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        UUID cartillaId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        org.mockito.BDDMockito.willThrow(new RecursoNoEncontradoException(
                        "No tiene permisos de edición sobre este paciente"))
                .given(pacienteService).verificarEdicionParaUsuario(pacienteId, familiar);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("No tiene permisos de edición");

        org.mockito.Mockito.verify(cartillaRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Terapeuta de OTRO paciente → NO puede actualizar la cartilla")
    void actualizar_terapeutaOtroPaciente_lanzaExcepcion() {
        UUID cartillaId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        org.mockito.BDDMockito.willThrow(new RecursoNoEncontradoException(
                        "Paciente no encontrado o no tiene permisos"))
                .given(pacienteService).verificarEdicionParaUsuario(pacienteId, terapeuta);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        org.mockito.Mockito.verify(cartillaRepository, org.mockito.Mockito.never()).save(any());
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

    // ──────────────────────────────────────────────
    //  DETALLE DE CARTILLA
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Detalle de cartilla → ensambla categorías, resuelve pictograma GLOBAL y CUSTOM")
    void obtenerDetalle_correcto_ensamblaYPictogramas() {
        UUID cartillaId = UUID.randomUUID();

        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .nombre("Cartilla A")
                .esPrincipal(true)
                .build();

        Categoria categoria = Categoria.builder()
                .id(UUID.randomUUID())
                .cartilla(cartilla)
                .nombre("Acciones")
                .colorHex("#FF0000")
                .orden(0)
                .build();

        PictogramaGlobal global = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("Correr")
                .imagenUrl("http://img/correr.png")
                .build();

        PictogramaCustom custom = PictogramaCustom.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .etiqueta("Mi foto")
                .imagenUrl("http://img/mi-foto.png")
                .build();

        ItemCartilla itemGlobal = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Correr")
                .ordenVisual(0)
                .recursoGlobal(global)
                .build();
        ItemCartilla itemCustom = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Mi foto")
                .ordenVisual(1)
                .recursoCustom(custom)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteService.pacienteLegibleParaUsuario(eq(pacienteId), eq(terapeuta))).willReturn(paciente);
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId)).willReturn(List.of(categoria));
        given(itemCartillaRepository.findByCategoriaIdOrderByOrdenVisualAsc(categoria.getId()))
                .willReturn(List.of(itemGlobal, itemCustom));
        given(pictogramaGlobalRepository.findById(global.getId())).willReturn(Optional.of(global));
        given(pictogramaCustomRepository.findById(custom.getId())).willReturn(Optional.of(custom));

        CartillaDetalleResponseDTO detalle =
                cartillaService.obtenerCartillaDetalle(pacienteId, cartillaId, "test@ejemplo.com");

        assertThat(detalle).isNotNull();
        assertThat(detalle.id()).isEqualTo(cartillaId);
        assertThat(detalle.nombre()).isEqualTo("Cartilla A");
        assertThat(detalle.esPrincipal()).isTrue();

        assertThat(detalle.categorias()).hasSize(1);
        CategoriaDetalleResponseDTO catDto = detalle.categorias().get(0);
        assertThat(catDto.nombre()).isEqualTo("Acciones");
        assertThat(catDto.colorHex()).isEqualTo("#FF0000");
        assertThat(catDto.orden()).isEqualTo(0);

        assertThat(catDto.items()).hasSize(2);
        ItemDetalleResponseDTO itemDtoGlobal = catDto.items().get(0);
        assertThat(itemDtoGlobal.pictograma()).isNotNull();
        assertThat(itemDtoGlobal.pictograma().tipo()).isEqualTo("GLOBAL");
        assertThat(itemDtoGlobal.pictograma().etiqueta()).isEqualTo("Correr");

        ItemDetalleResponseDTO itemDtoCustom = catDto.items().get(1);
        assertThat(itemDtoCustom.pictograma()).isNotNull();
        assertThat(itemDtoCustom.pictograma().tipo()).isEqualTo("CUSTOM");
        assertThat(itemDtoCustom.pictograma().etiqueta()).isEqualTo("Mi foto");
    }

    @Test
    @DisplayName("Detalle de cartilla → pictograma null cuando el recurso referenciado no existe")
    void obtenerDetalle_recursoInexistente_pictogramaNull() {
        UUID cartillaId = UUID.randomUUID();

        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .nombre("Cartilla A")
                .esPrincipal(false)
                .build();

        Categoria categoria = Categoria.builder()
                .id(UUID.randomUUID())
                .cartilla(cartilla)
                .nombre("Acciones")
                .colorHex("#00FF00")
                .orden(0)
                .build();

        // Item con recurso global cuyo id ya no existe en el repositorio
        PictogramaGlobal globalPerdido = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("Fantasma")
                .imagenUrl("http://img/fantasma.png")
                .build();

        ItemCartilla item = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Fantasma")
                .ordenVisual(0)
                .recursoGlobal(globalPerdido)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteService.pacienteLegibleParaUsuario(eq(pacienteId), eq(terapeuta))).willReturn(paciente);
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId)).willReturn(List.of(categoria));
        given(itemCartillaRepository.findByCategoriaIdOrderByOrdenVisualAsc(categoria.getId()))
                .willReturn(List.of(item));
        given(pictogramaGlobalRepository.findById(globalPerdido.getId())).willReturn(Optional.empty());

        CartillaDetalleResponseDTO detalle =
                cartillaService.obtenerCartillaDetalle(pacienteId, cartillaId, "test@ejemplo.com");

        // El item NO se excluye: queda con pictograma null (el front lo maneja con placeholder)
        assertThat(detalle.categorias()).hasSize(1);
        assertThat(detalle.categorias().get(0).items()).hasSize(1);
        assertThat(detalle.categorias().get(0).items().get(0).pictograma()).isNull();
    }

    @Test
    @DisplayName("Detalle de cartilla inexistente o de otro paciente → lanza excepción genérica")
    void obtenerDetalle_cartillaInexistente_lanzaExcepcion() {
        UUID cartillaId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(pacienteService.pacienteLegibleParaUsuario(eq(pacienteId), eq(terapeuta))).willReturn(paciente);
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartillaService.obtenerCartillaDetalle(pacienteId, cartillaId, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
    }
}
