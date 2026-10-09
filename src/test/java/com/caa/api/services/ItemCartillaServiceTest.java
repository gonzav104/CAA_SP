package com.caa.api.services;

import com.caa.api.dtos.ItemCartillaActualizacionDTO;
import com.caa.api.dtos.ItemCartillaRegistroDTO;
import com.caa.api.dtos.ItemCartillaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
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

/**
 * Tarea 3.4: migrado de ownership por creador ({@code cartillaRepository.findByIdAndPacienteIdAndCreadorId})
 * a {@code AccesoService.exigirCapacidad(EDITAR_CONTENIDO)} + {@code cartillaRepository.findByIdAndPacienteId}
 * (design-part2 §11.2, R2). La regla XOR (recursoGlobal/recursoCustom) y el ownership del
 * pictograma custom sobre el paciente quedan sin cambios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemCartillaService — Tests unitarios (XOR + AccesoService)")
class ItemCartillaServiceTest {

    @Mock private ItemCartillaRepository itemCartillaRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private CartillaRepository cartillaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PictogramaGlobalRepository pictogramaGlobalRepository;
    @Mock private PictogramaCustomRepository pictogramaCustomRepository;
    @Mock private AccesoService accesoService;

    @InjectMocks private ItemCartillaServiceImpl itemCartillaService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID organizacionId;
    private UUID cartillaId;
    private UUID categoriaId;
    private UUID itemId;
    private Usuario terapeuta;
    private Organizacion organizacion;
    private Paciente paciente;
    private Cartilla cartilla;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        organizacionId = UUID.randomUUID();
        cartillaId = UUID.randomUUID();
        categoriaId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder().id(pacienteId).organizacion(organizacion).build();
        cartilla = Cartilla.builder()
                .id(cartillaId).paciente(paciente).creador(terapeuta).nombre("Cartilla").esPrincipal(false).build();
        categoria = Categoria.builder().id(categoriaId).cartilla(cartilla).nombre("Acciones").build();
    }

    private Membresia membresiaDe(RolGestion rolGestion, boolean esTerapeuta) {
        return Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeutaId))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(rolGestion)
                .esTerapeuta(esTerapeuta)
                .build();
    }

    private AccesoPaciente accesoDeEquipo() {
        return new AccesoPaciente(paciente, membresiaDe(RolGestion.OWNER, true), false, null);
    }

    // ──────────────────────────────────────────────
    //  ACCESO: gate de AccesoService + cartilla del paciente
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Sin acceso de EDITAR_CONTENIDO al paciente → 404 genérico ANTES de la regla XOR")
    void crear_sinAcceso_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

        // Aunque el dto viole XOR (ambos null), el gate de acceso gana
        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, null, null, null, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(itemCartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cartilla inexistente o de otro paciente → 404 genérico ANTES de la regla XOR")
    void crear_cartillaInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.empty());

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, null, null, null, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(itemCartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Miembro de equipo → puede actualizar el item aunque no haya creado la cartilla")
    void actualizar_equipoDeGestion_puedeActualizar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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

        ItemCartillaActualizacionDTO dto = new ItemCartillaActualizacionDTO("Hola", 1, globalId, null, null, null, null);

        ItemCartillaResponseDTO response =
                itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId, itemId, dto, "test@ejemplo.com");

        assertThat(response.textoHablado()).isEqualTo("Hola");
        assertThat(response.recursoGlobalId()).isEqualTo(globalId);
    }

    @Test
    @DisplayName("Miembro de equipo → puede eliminar el item aunque no haya creado la cartilla")
    void eliminar_equipoDeGestion_puedeEliminar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
        ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria).build();
        given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)).willReturn(Optional.of(item));

        itemCartillaService.eliminarItem(pacienteId, cartillaId, categoriaId, itemId, "test@ejemplo.com");

        verify(itemCartillaRepository).delete(item);
    }

    @Test
    @DisplayName("Cartilla inexistente o de otro paciente → NO puede eliminar el item (404 genérico)")
    void eliminar_cartillaInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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
        prepararAcceso();

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, null, null, null, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente un recurso");

        verify(itemCartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Item con AMBOS recursos → lanza excepción por XOR")
    void crear_ambosRecursos_lanzaXor() {
        prepararAcceso();

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, UUID.randomUUID(), UUID.randomUUID(), null, null, null);

        assertThatThrownBy(() ->
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente un recurso");
    }

    @Test
    @DisplayName("Item con recursoCustom de OTRO paciente → lanza excepción (ownership del recurso)")
    void crear_customDeOtroPaciente_lanzaExcepcion() {
        prepararAcceso();

        UUID customId = UUID.randomUUID();
        UUID otroPacienteId = UUID.randomUUID();
        PictogramaCustom custom = PictogramaCustom.builder()
                .id(customId)
                .paciente(Paciente.builder().id(otroPacienteId).build()) // otro paciente
                .build();

        given(pictogramaCustomRepository.findById(customId)).willReturn(Optional.of(custom));

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, customId, null, null, null);

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
        prepararAcceso();

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

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, globalId, null, null, null, null);

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
        prepararAcceso();

        UUID customId = UUID.randomUUID();
        PictogramaCustom custom = PictogramaCustom.builder().id(customId).paciente(paciente).etiqueta("Foto").build();
        given(pictogramaCustomRepository.findById(customId)).willReturn(Optional.of(custom));

        ItemCartilla guardado = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Hola")
                .ordenVisual(1)
                .recursoCustom(custom)
                .build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, null, customId, null, null, null);

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
        prepararAcceso();

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

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, globalId, null, true, null, null);

        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response.esCore()).isTrue();
        verify(itemCartillaRepository).save(argThat(ItemCartilla::isEsCore));
    }

    @Test
    @DisplayName("Crear item sin esCore (null) → default false (comportamiento actual / backfill)")
    void crear_sinEsCore_defaultFalse() {
        prepararAcceso();

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

        ItemCartillaRegistroDTO dto = new ItemCartillaRegistroDTO("Hola", 1, globalId, null, null, null, null);

        ItemCartillaResponseDTO response =
                itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");

        assertThat(response.esCore()).isFalse();
        verify(itemCartillaRepository).save(argThat(i -> !i.isEsCore()));
    }

    @Test
    @DisplayName("Actualizar con esCore=false explícito → desmarca el item (update parcial)")
    void actualizar_conEsCoreFalse_desmarcaCore() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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

        ItemCartillaActualizacionDTO dto = new ItemCartillaActualizacionDTO("Hola", 1, globalId, null, false, null, null);

        itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId, itemId, dto, "test@ejemplo.com");

        verify(itemCartillaRepository).save(argThat(i -> !i.isEsCore()));
    }

    @Test
    @DisplayName("Actualizar sin campo esCore (null) → PRESERVA el valor existente")
    void actualizar_sinEsCore_preservaValor() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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

        ItemCartillaActualizacionDTO dto = new ItemCartillaActualizacionDTO("Hola", 1, globalId, null, null, null, null);

        itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId, itemId, dto, "test@ejemplo.com");

        verify(itemCartillaRepository).save(argThat(ItemCartilla::isEsCore));
    }

    // ──────────────────────────────────────────────
    //  TEXTO_VISIBLE / VISIBLE_EN_MODO_USO
    // ──────────────────────────────────────────────

    private PictogramaGlobal prepararGlobal(String etiqueta) {
        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta(etiqueta).build();
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));
        return global;
    }

    private void guardarDevolviendoArgumento() {
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private ItemCartillaResponseDTO crear(UUID globalId, UUID customId, String textoVisible, Boolean visible) {
        ItemCartillaRegistroDTO dto =
                new ItemCartillaRegistroDTO("Quiero ir al baño", 1, globalId, customId, null, textoVisible, visible);
        return itemCartillaService.crearItem(pacienteId, cartillaId, categoriaId, dto, "test@ejemplo.com");
    }

    @Test
    @DisplayName("Crear con textoVisible explícito → se guarda recortado (trim)")
    void crear_conTextoVisible_seGuardaTrimmeado() {
        prepararAcceso();
        PictogramaGlobal global = prepararGlobal("Baño");
        guardarDevolviendoArgumento();

        ItemCartillaResponseDTO response = crear(global.getId(), null, "  BAÑO  ", null);

        assertThat(response.textoVisible()).isEqualTo("BAÑO");
        assertThat(response.textoHablado()).isEqualTo("Quiero ir al baño");
    }

    @Test
    @DisplayName("Crear sin textoVisible → fallback a la etiqueta del pictograma global")
    void crear_sinTextoVisible_fallbackEtiquetaGlobal() {
        prepararAcceso();
        PictogramaGlobal global = prepararGlobal("  Baño ");
        guardarDevolviendoArgumento();

        ItemCartillaResponseDTO response = crear(global.getId(), null, null, null);

        assertThat(response.textoVisible()).isEqualTo("Baño");
    }

    @Test
    @DisplayName("Crear sin textoVisible → fallback a la etiqueta del pictograma custom, cortada a 30")
    void crear_sinTextoVisible_fallbackEtiquetaCustomCortadaA30() {
        prepararAcceso();
        UUID customId = UUID.randomUUID();
        String etiquetaLarga = "A".repeat(40);
        PictogramaCustom custom = PictogramaCustom.builder()
                .id(customId).paciente(paciente).etiqueta(etiquetaLarga).build();
        given(pictogramaCustomRepository.findById(customId)).willReturn(Optional.of(custom));
        guardarDevolviendoArgumento();

        ItemCartillaResponseDTO response = crear(null, customId, null, null);

        assertThat(response.textoVisible()).isEqualTo("A".repeat(30));
    }

    @Test
    @DisplayName("Crear: visibleEnModoUso null → true; explícito false → false")
    void crear_visibleEnModoUso_defaultTrueYExplicitoFalse() {
        prepararAcceso();
        PictogramaGlobal global = prepararGlobal("Baño");
        guardarDevolviendoArgumento();

        assertThat(crear(global.getId(), null, null, null).visibleEnModoUso()).isTrue();
        assertThat(crear(global.getId(), null, null, false).visibleEnModoUso()).isFalse();
    }

    private ItemCartilla prepararActualizacion(String textoVisible, boolean visible) {
        prepararAcceso();
        ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria)
                .textoHablado("Hola").textoVisible(textoVisible).visibleEnModoUso(visible).build();
        given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)).willReturn(Optional.of(item));
        guardarDevolviendoArgumento();
        return item;
    }

    @Test
    @DisplayName("Actualizar con textoVisible y visibleEnModoUso null → conserva ambos valores")
    void actualizar_conNulls_conservaValores() {
        prepararActualizacion("BAÑO", false);
        PictogramaGlobal global = prepararGlobal("Otro");

        ItemCartillaResponseDTO response = itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId,
                itemId, new ItemCartillaActualizacionDTO("Hola", 1, global.getId(), null, null, null, null),
                "test@ejemplo.com");

        assertThat(response.textoVisible()).isEqualTo("BAÑO");
        assertThat(response.visibleEnModoUso()).isFalse();
    }

    @Test
    @DisplayName("Actualizar con valores nuevos → cambia textoVisible (trim) y visibleEnModoUso")
    void actualizar_conValores_loscambia() {
        prepararActualizacion("BAÑO", false);
        PictogramaGlobal global = prepararGlobal("Otro");

        ItemCartillaResponseDTO response = itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId,
                itemId, new ItemCartillaActualizacionDTO("Hola", 1, global.getId(), null, null, "  COMER ", true),
                "test@ejemplo.com");

        assertThat(response.textoVisible()).isEqualTo("COMER");
        assertThat(response.visibleEnModoUso()).isTrue();
    }

    @Test
    @DisplayName("Actualizar cambiando el pictograma → NO pisa textoVisible")
    void actualizar_cambiandoPictograma_noPisaTextoVisible() {
        ItemCartilla item = prepararActualizacion("BAÑO", true);
        PictogramaGlobal nuevo = prepararGlobal("Etiqueta distinta");

        ItemCartillaResponseDTO response = itemCartillaService.actualizarItem(pacienteId, cartillaId, categoriaId,
                itemId, new ItemCartillaActualizacionDTO("Hola", 1, nuevo.getId(), null, null, null, null),
                "test@ejemplo.com");

        assertThat(item.getRecursoGlobal()).isSameAs(nuevo);
        assertThat(response.textoVisible()).isEqualTo("BAÑO");
    }

    private void prepararAcceso() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
    }
}
