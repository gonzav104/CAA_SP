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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartillaService — Tests unitarios")
class CartillaServiceTest {

    @Mock private CartillaRepository cartillaRepository;
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

    // ──────────────────────────────────────────────
    //  CREAR (gate de rol: verificarEdicionParaUsuario)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Crear cartilla: terapeuta dueño → gate OK y queda con creador = terapeuta")
    void crear_terapeuta_asignaCreador() {
        Cartilla cartilla = Cartilla.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .creador(terapeuta)
                .nombre("Cartilla A")
                .esPrincipal(true)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        willDoNothing().given(pacienteService).verificarEdicionParaUsuario(pacienteId, terapeuta);
        given(pacienteService.pacienteLegibleParaUsuario(eq(pacienteId), eq(terapeuta))).willReturn(paciente);
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartilla);

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", true);
        CartillaResponseDTO response = cartillaService.crearCartilla(pacienteId, dto, "test@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Cartilla A");
        assertThat(response.pacienteId()).isEqualTo(pacienteId);
        assertThat(response.esPrincipal()).isTrue();

        ArgumentCaptor<Cartilla> captor = ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getCreador()).isSameAs(terapeuta);
    }

    @Test
    @DisplayName("Crear cartilla: familiar con EDICION_LIMITADA → queda con creador = familiar")
    void crear_familiarEdicionLimitada_asignaCreador() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();
        Cartilla cartilla = Cartilla.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .creador(familiar)
                .nombre("Cartilla F")
                .esPrincipal(false)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willDoNothing().given(pacienteService).verificarEdicionParaUsuario(pacienteId, familiar);
        given(pacienteService.pacienteLegibleParaUsuario(eq(pacienteId), eq(familiar))).willReturn(paciente);
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartilla);

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla F", false);
        CartillaResponseDTO response = cartillaService.crearCartilla(pacienteId, dto, "familiar@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Cartilla F");

        ArgumentCaptor<Cartilla> captor = ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getCreador()).isSameAs(familiar);
    }

    @Test
    @DisplayName("Crear cartilla: familiar con LECTURA → 404 genérico (no encontrado o sin permisos)")
    void crear_familiarLectura_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(pacienteService).verificarEdicionParaUsuario(pacienteId, familiar);

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false);

        assertThatThrownBy(() -> cartillaService.crearCartilla(pacienteId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Usuario inexistente → lanza excepción")
    void crear_usuarioInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false);

        assertThatThrownBy(() -> cartillaService.crearCartilla(pacienteId, dto, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    // ──────────────────────────────────────────────
    //  ACTUALIZAR (gate: creador-lookup único)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Creador → puede actualizar su cartilla")
    void actualizar_creador_puedeActualizar() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(terapeuta)
                .nombre("Nombre viejo")
                .esPrincipal(false)
                .build();
        Cartilla cartillaActualizada = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(terapeuta)
                .nombre("Nombre nuevo")
                .esPrincipal(true)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartillaActualizada);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", true);

        CartillaResponseDTO response = cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Nombre nuevo");
        assertThat(response.esPrincipal()).isTrue();
    }

    @Test
    @DisplayName("Actualizar cartilla que no pertenece al paciente → lanza excepción (ownership anidado)")
    void actualizar_cartillaDeOtroPaciente_lanzaExcepcion() {
        UUID cartillaId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nuevo nombre", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Familiar con EDICION_LIMITADA que NO es creador → 404 genérico (regresión revertida)")
    void actualizar_familiarNoCreador_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        UUID cartillaId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, familiarId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Familiar con LECTURA → NO puede actualizar la cartilla (404 genérico)")
    void actualizar_familiarLectura_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        UUID cartillaId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, familiarId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Terapeuta de OTRO paciente → NO puede actualizar la cartilla (404 genérico)")
    void actualizar_terapeutaOtroPaciente_lanzaExcepcion() {
        UUID cartillaId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Terapeuta en cartilla de un familiar → NO puede actualizarla (404 genérico)")
    void actualizar_terapeutaEnCartillaDeFamiliar_lanzaExcepcion() {
        UUID cartillaId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  ELIMINAR (gate: creador-lookup único)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Creador → puede eliminar su cartilla")
    void eliminar_creador_puedeEliminar() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(terapeuta)
                .nombre("Cartilla A")
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, terapeutaId))
                .willReturn(Optional.of(cartilla));

        cartillaService.eliminarCartilla(pacienteId, cartillaId, "test@ejemplo.com");

        verify(cartillaRepository).delete(cartilla);
    }

    @Test
    @DisplayName("No-creador → NO puede eliminar la cartilla (404 genérico)")
    void eliminar_noCreador_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        UUID cartillaId = UUID.randomUUID();
        Usuario familiar = Usuario.builder()
                .id(familiarId)
                .email("familiar@ejemplo.com")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaId, pacienteId, familiarId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                cartillaService.eliminarCartilla(pacienteId, cartillaId, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(cartillaRepository, never()).delete(any());
    }

    // ──────────────────────────────────────────────
    //  esPrincipal libre (sin unicidad)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Dos creadores distintos marcan su cartilla como principal → ambos OK (sin unicidad)")
    void dosCreadores_esPrincipalTrue_ambosOK() {
        UUID cartillaA = UUID.randomUUID();
        UUID cartillaB = UUID.randomUUID();
        UUID creadorAId = UUID.randomUUID();
        UUID creadorBId = UUID.randomUUID();
        Usuario creadorA = Usuario.builder()
                .id(creadorAId).email("creadorA@ejemplo.com").rol(RolUsuario.TERAPEUTA).build();
        Usuario creadorB = Usuario.builder()
                .id(creadorBId).email("creadorB@ejemplo.com").rol(RolUsuario.TERAPEUTA).build();
        Cartilla cartillaDeA = Cartilla.builder()
                .id(cartillaA).paciente(paciente).creador(creadorA).nombre("A").esPrincipal(false).build();
        Cartilla cartillaDeB = Cartilla.builder()
                .id(cartillaB).paciente(paciente).creador(creadorB).nombre("B").esPrincipal(false).build();

        given(usuarioRepository.findByEmail("creadorA@ejemplo.com")).willReturn(Optional.of(creadorA));
        given(usuarioRepository.findByEmail("creadorB@ejemplo.com")).willReturn(Optional.of(creadorB));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaA, pacienteId, creadorAId))
                .willReturn(Optional.of(cartillaDeA));
        given(cartillaRepository.findByIdAndPacienteIdAndCreadorId(cartillaB, pacienteId, creadorBId))
                .willReturn(Optional.of(cartillaDeB));
        given(cartillaRepository.save(cartillaDeA)).willReturn(cartillaDeA);
        given(cartillaRepository.save(cartillaDeB)).willReturn(cartillaDeB);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Principal", true);

        CartillaResponseDTO r1 = cartillaService.actualizarCartilla(pacienteId, cartillaA, dto, "creadorA@ejemplo.com");
        CartillaResponseDTO r2 = cartillaService.actualizarCartilla(pacienteId, cartillaB, dto, "creadorB@ejemplo.com");

        assertThat(r1.esPrincipal()).isTrue();
        assertThat(r2.esPrincipal()).isTrue();
    }

    // ──────────────────────────────────────────────
    //  LECTURA (sin cambios: abierta a cualquier lector)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Obtener cartillas de paciente legible → devuelve lista ordenada")
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