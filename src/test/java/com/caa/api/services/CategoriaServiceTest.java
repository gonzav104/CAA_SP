package com.caa.api.services;

import com.caa.api.dtos.CategoriaActualizacionDTO;
import com.caa.api.dtos.CategoriaRegistroDTO;
import com.caa.api.dtos.CategoriaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tarea 3.4: migrado de ownership por creador ({@code cartillaRepository.findByIdAndPacienteIdAndCreadorId})
 * a {@code AccesoService.exigirCapacidad(EDITAR_CONTENIDO)} + {@code cartillaRepository.findByIdAndPacienteId}
 * (design-part2 §11.2, R2). El acceso ahora deriva del paciente, nunca de quién creó la cartilla.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoriaService — Tests unitarios (AccesoService)")
class CategoriaServiceTest {

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private CartillaRepository cartillaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AccesoService accesoService;

    @InjectMocks private CategoriaServiceImpl categoriaService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID organizacionId;
    private UUID cartillaId;
    private UUID categoriaId;
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
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder().id(pacienteId).organizacion(organizacion).build();
        cartilla = Cartilla.builder()
                .id(cartillaId).paciente(paciente).creador(terapeuta).nombre("Cartilla").esPrincipal(false).build();
        categoria = Categoria.builder()
                .id(categoriaId).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").orden(1).build();
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
    //  CREAR (gate: EDITAR_CONTENIDO + findByIdAndPacienteId)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → puede crear categoría")
    void crear_equipoDeGestion_creaSobreLaCartilla() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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
    @DisplayName("Cartilla inexistente o de otro paciente → 404 genérico")
    void crear_cartillaDeOtroPaciente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.empty());

        CategoriaRegistroDTO dto = new CategoriaRegistroDTO("Comida", "#E0E0E0", null);

        assertThatThrownBy(() ->
                categoriaService.crearCategoria(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Familiar sin acceso de EDITAR_CONTENIDO → 404 genérico")
    void crear_familiarSinAcceso_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder().id(familiarId).email("familiar@ejemplo.com").build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO);

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
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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
    //  ACTUALIZAR / ELIMINAR (gate: EDITAR_CONTENIDO + findByIdAndPacienteId)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → puede actualizar la categoría aunque no haya creado la cartilla")
    void actualizar_equipoDeGestion_puedeActualizar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
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
    @DisplayName("Miembro de equipo → puede eliminar la categoría aunque no haya creado la cartilla")
    void eliminar_equipoDeGestion_puedeEliminar() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));

        categoriaService.eliminarCategoria(pacienteId, cartillaId, categoriaId, "test@ejemplo.com");

        verify(categoriaRepository).delete(categoria);
    }

    @Test
    @DisplayName("Cartilla inexistente o de otro paciente → NO puede eliminar la categoría (404 genérico)")
    void eliminar_cartillaInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                categoriaService.eliminarCategoria(pacienteId, cartillaId, categoriaId, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(categoriaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Familiar con LECTURA (sin EDITAR_CONTENIDO) → NO puede eliminar la categoría (404 genérico)")
    void eliminar_familiarLectura_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder().id(familiarId).email("familiar@ejemplo.com").build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO);

        assertThatThrownBy(() ->
                categoriaService.eliminarCategoria(pacienteId, cartillaId, categoriaId, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(categoriaRepository, never()).delete(any());
    }
}
