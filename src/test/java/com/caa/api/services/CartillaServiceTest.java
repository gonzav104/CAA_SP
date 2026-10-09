package com.caa.api.services;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaDetalleResponseDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.dtos.CategoriaDetalleResponseDTO;
import com.caa.api.dtos.ItemDetalleResponseDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.ParadigmaCartilla;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.CartillaServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tarea 3.1-3.3: migrado de {@code creador_id}/{@code findByIdAndPacienteIdAndCreadorId}
 * (ownership por creador) a {@code AccesoService.exigirCapacidad(...)} (design-part2 §11.2,
 * regla R2 — "access derives from patient access, never from creador_id"). El fixture-helper
 * {@code membresiaDe(rolGestion, esTerapeuta)} sigue el idiom compartido de la tarea 2.9/3.6.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartillaService — Tests unitarios (AccesoService)")
class CartillaServiceTest {

    @Mock private CartillaRepository cartillaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ItemCartillaRepository itemCartillaRepository;
    @Mock private AccesoService accesoService;

    @InjectMocks private CartillaServiceImpl cartillaService;

    private UUID terapeutaId;
    private UUID pacienteId;
    private UUID organizacionId;
    private Usuario terapeuta;
    private Organizacion organizacion;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        terapeutaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        organizacionId = UUID.randomUUID();
        terapeuta = Usuario.builder().id(terapeutaId).email("test@ejemplo.com").build();
        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder().id(pacienteId).organizacion(organizacion).build();
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

    private AccesoPaciente accesoFamiliar(PermisoColaborador permiso) {
        return new AccesoPaciente(paciente, null, false, permiso);
    }

    // ──────────────────────────────────────────────
    //  CREAR (gate de acceso: EDITAR_CONTENIDO)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Crear cartilla: miembro de equipo → gate OK y queda con creador = usuario")
    void crear_equipoDeGestion_asignaCreador() {
        Cartilla cartilla = Cartilla.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .creador(terapeuta)
                .nombre("Cartilla A")
                .esPrincipal(true)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartilla);

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", true, null);
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
        Usuario familiar = Usuario.builder().id(familiarId).email("familiar@ejemplo.com").build();
        Cartilla cartilla = Cartilla.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .creador(familiar)
                .nombre("Cartilla F")
                .esPrincipal(false)
                .build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                .willReturn(new AccesoPaciente(paciente, null, false, PermisoColaborador.EDICION_LIMITADA));
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartilla);

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla F", false, null);
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
        Usuario familiar = Usuario.builder().id(familiarId).email("familiar@ejemplo.com").build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO);

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false, null);

        assertThatThrownBy(() -> cartillaService.crearCartilla(pacienteId, dto, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Usuario inexistente → lanza excepción")
    void crear_usuarioInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false, null);

        assertThatThrownBy(() -> cartillaService.crearCartilla(pacienteId, dto, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    // ──────────────────────────────────────────────
    //  CREAR — paradigma de organización del tablero
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Crear cartilla: sin paradigma explícito → default TAXONOMICA")
    void crear_sinParadigma_defaultTaxonomica() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false, null);
        cartillaService.crearCartilla(pacienteId, dto, "test@ejemplo.com");

        ArgumentCaptor<Cartilla> captor = ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getParadigma()).isEqualTo(ParadigmaCartilla.TAXONOMICA);
    }

    @Test
    @DisplayName("Crear cartilla: con paradigma ESQUEMATICA explícito → se respeta")
    void crear_conParadigmaEsquematica_seRespeta() {
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CartillaRegistroDTO dto = new CartillaRegistroDTO("Cartilla A", false, ParadigmaCartilla.ESQUEMATICA);
        cartillaService.crearCartilla(pacienteId, dto, "test@ejemplo.com");

        ArgumentCaptor<Cartilla> captor = ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getParadigma()).isEqualTo(ParadigmaCartilla.ESQUEMATICA);
    }

    // ──────────────────────────────────────────────
    //  ACTUALIZAR (gate: EDITAR_CONTENIDO + findByIdAndPacienteId)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → puede actualizar la cartilla aunque no la haya creado")
    void actualizar_equipoDeGestion_puedeActualizar() {
        UUID cartillaId = UUID.randomUUID();
        UUID otroCreadorId = UUID.randomUUID();
        Usuario otroCreador = Usuario.builder().id(otroCreadorId).build();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(otroCreador)
                .nombre("Nombre viejo")
                .esPrincipal(false)
                .build();
        Cartilla cartillaActualizada = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(otroCreador)
                .nombre("Nombre nuevo")
                .esPrincipal(true)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(cartillaActualizada);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", true, null);

        CartillaResponseDTO response = cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com");

        assertThat(response.nombre()).isEqualTo("Nombre nuevo");
        assertThat(response.esPrincipal()).isTrue();
    }

    @Test
    @DisplayName("Actualizar cartilla: paradigma omitido (null) → no se toca el valor existente")
    void actualizar_sinParadigma_noTocaValorExistente() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(terapeuta)
                .nombre("Nombre viejo")
                .esPrincipal(false)
                .paradigma(ParadigmaCartilla.ESQUEMATICA)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", null, null);
        cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com");

        ArgumentCaptor<Cartilla> captor = ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getParadigma()).isEqualTo(ParadigmaCartilla.ESQUEMATICA);
    }

    @Test
    @DisplayName("Actualizar cartilla: paradigma explícito → sobrescribe el valor existente")
    void actualizar_conParadigma_sobrescribeValorExistente() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(terapeuta)
                .nombre("Nombre viejo")
                .esPrincipal(false)
                .paradigma(ParadigmaCartilla.TAXONOMICA)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", null, ParadigmaCartilla.ESQUEMATICA);
        cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com");

        ArgumentCaptor<Cartilla> captor = ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getParadigma()).isEqualTo(ParadigmaCartilla.ESQUEMATICA);
    }

    @Test
    @DisplayName("Actualizar cartilla que no pertenece al paciente → lanza excepción (ownership anidado)")
    void actualizar_cartillaDeOtroPaciente_lanzaExcepcion() {
        UUID cartillaId = UUID.randomUUID();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.empty());

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nuevo nombre", false, null);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Familiar con LECTURA → NO puede actualizar la cartilla (404 genérico)")
    void actualizar_familiarLectura_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        UUID cartillaId = UUID.randomUUID();
        Usuario familiar = Usuario.builder().id(familiarId).email("familiar@ejemplo.com").build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false, null);

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
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO);

        CartillaActualizacionDTO dto = new CartillaActualizacionDTO("Nombre nuevo", false, null);

        assertThatThrownBy(() ->
                cartillaService.actualizarCartilla(pacienteId, cartillaId, dto, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");

        verify(cartillaRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  ELIMINAR (gate: GESTION_CLINICA + findByIdAndPacienteId)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo → puede eliminar la cartilla aunque no la haya creado")
    void eliminar_equipoDeGestion_puedeEliminar() {
        UUID cartillaId = UUID.randomUUID();
        UUID otroCreadorId = UUID.randomUUID();
        Usuario otroCreador = Usuario.builder().id(otroCreadorId).build();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(otroCreador)
                .nombre("Cartilla A")
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));

        cartillaService.eliminarCartilla(pacienteId, cartillaId, "test@ejemplo.com");

        verify(cartillaRepository).delete(cartilla);
    }

    @Test
    @DisplayName("Familiar (EDICION_LIMITADA, nunca borra) → NO puede eliminar la cartilla (404 genérico)")
    void eliminar_familiar_lanzaExcepcion() {
        UUID familiarId = UUID.randomUUID();
        UUID cartillaId = UUID.randomUUID();
        Usuario familiar = Usuario.builder().id(familiarId).email("familiar@ejemplo.com").build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() ->
                cartillaService.eliminarCartilla(pacienteId, cartillaId, "familiar@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(cartillaRepository, never()).delete(any());
    }

    // ──────────────────────────────────────────────
    //  esPrincipal único por paciente (permiso + orden de operaciones)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Actualizar a principal: miembro de equipo → desmarca las otras ANTES de guardar")
    void actualizar_equipoDeGestion_desmarcaOtrasAntesDeGuardar() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId).paciente(paciente).creador(terapeuta).nombre("A").esPrincipal(false).build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(cartilla)).willReturn(cartilla);

        CartillaResponseDTO r = cartillaService.actualizarCartilla(
                pacienteId, cartillaId, new CartillaActualizacionDTO("Principal", true, null), "test@ejemplo.com");

        assertThat(r.esPrincipal()).isTrue();
        InOrder orden = inOrder(cartillaRepository);
        orden.verify(cartillaRepository).desmarcarOtrasPrincipalesDe(pacienteId, cartillaId);
        orden.verify(cartillaRepository).save(cartilla);
    }

    @Test
    @DisplayName("Actualizar a principal: familiar con EDICION_LIMITADA (sin acceso de equipo) → 403 y sin escrituras")
    void actualizar_familiarSinEquipo_accesoDenegado() {
        UUID cartillaId = UUID.randomUUID();
        UUID creadorId = UUID.randomUUID();
        Usuario familiar = Usuario.builder().id(creadorId).email("familiar@ejemplo.com").build();
        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId).paciente(paciente).creador(familiar).nombre("F").esPrincipal(false).build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoFamiliar(PermisoColaborador.EDICION_LIMITADA));
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));

        assertThatThrownBy(() -> cartillaService.actualizarCartilla(pacienteId, cartillaId,
                new CartillaActualizacionDTO("Principal", true, null), "familiar@ejemplo.com"))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("equipo");

        verify(cartillaRepository, never()).desmarcarOtrasPrincipalesDe(any(), any());
        verify(cartillaRepository, never()).save(any());
        assertThat(cartilla.isEsPrincipal()).isFalse();
    }

    @Test
    @DisplayName("Actualizar sin cambiar esPrincipal (true sobre la principal / false sobre una común) → sin efectos")
    void actualizar_sinCambioDePrincipal_noDesmarcaNada() {
        UUID principalId = UUID.randomUUID();
        UUID comunId = UUID.randomUUID();
        Cartilla principal = Cartilla.builder()
                .id(principalId).paciente(paciente).creador(terapeuta).nombre("P").esPrincipal(true).build();
        Cartilla comun = Cartilla.builder()
                .id(comunId).paciente(paciente).creador(terapeuta).nombre("C").esPrincipal(false).build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(principalId, pacienteId))
                .willReturn(Optional.of(principal));
        given(cartillaRepository.findByIdAndPacienteId(comunId, pacienteId))
                .willReturn(Optional.of(comun));
        given(cartillaRepository.save(any(Cartilla.class))).willAnswer(i -> i.getArgument(0));

        cartillaService.actualizarCartilla(
                pacienteId, principalId, new CartillaActualizacionDTO("P2", true, null), "test@ejemplo.com");
        cartillaService.actualizarCartilla(
                pacienteId, comunId, new CartillaActualizacionDTO("C2", false, null), "test@ejemplo.com");

        verify(cartillaRepository, never()).desmarcarOtrasPrincipalesDe(any(), any());
        assertThat(principal.isEsPrincipal()).isTrue();
        assertThat(comun.isEsPrincipal()).isFalse();
    }

    @Test
    @DisplayName("Crear con esPrincipal=true: desmarca las principales ANTES de guardar; familiar → 403")
    void crear_principal_desmarcaAntesDeGuardar_yFamiliarDenegado() {
        UUID familiarId = UUID.randomUUID();
        Usuario familiar = Usuario.builder().id(familiarId).email("familiar@ejemplo.com").build();

        given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
        given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoFamiliar(PermisoColaborador.EDICION_LIMITADA));

        assertThatThrownBy(() -> cartillaService.crearCartilla(
                pacienteId, new CartillaRegistroDTO("X", true, null), "familiar@ejemplo.com"))
                .isInstanceOf(AccesoDenegadoException.class);
        verify(cartillaRepository, never()).desmarcarPrincipalesDe(any());
        verify(cartillaRepository, never()).save(any());

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.save(any(Cartilla.class))).willAnswer(i -> i.getArgument(0));

        cartillaService.crearCartilla(pacienteId, new CartillaRegistroDTO("P", true, null), "test@ejemplo.com");

        InOrder orden = inOrder(cartillaRepository);
        orden.verify(cartillaRepository).desmarcarPrincipalesDe(pacienteId);
        orden.verify(cartillaRepository).save(any(Cartilla.class));
    }

    // ──────────────────────────────────────────────
    //  LECTURA (sin cambios: abierta a cualquier lector con acceso LEER)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Obtener cartillas de paciente legible → devuelve lista ordenada con creadorId")
    void obtener_correcto_devuelveLista() {
        Cartilla c1 = Cartilla.builder().id(UUID.randomUUID()).paciente(paciente).creador(terapeuta)
                .nombre("B").esPrincipal(false).build();
        Cartilla c2 = Cartilla.builder().id(UUID.randomUUID()).paciente(paciente).creador(terapeuta)
                .nombre("A").esPrincipal(true).build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByPacienteId(pacienteId)).willReturn(List.of(c1, c2));

        List<CartillaResponseDTO> resultado = cartillaService.obtenerCartillasDePaciente(pacienteId, "test@ejemplo.com");

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).nombre()).isEqualTo("B");
        assertThat(resultado.get(1).nombre()).isEqualTo("A");
        assertThat(resultado).extracting(CartillaResponseDTO::creadorId)
                .containsExactly(terapeutaId, terapeutaId);
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
                .creador(terapeuta)
                .nombre("Cartilla A")
                .esPrincipal(true)
                .paradigma(ParadigmaCartilla.ESQUEMATICA)
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
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId)).willReturn(List.of(categoria));
        given(itemCartillaRepository.findByCategoriaIdInOrderByOrdenVisualAsc(List.of(categoria.getId())))
                .willReturn(List.of(itemGlobal, itemCustom));

        CartillaDetalleResponseDTO detalle =
                cartillaService.obtenerCartillaDetalle(pacienteId, cartillaId, "test@ejemplo.com");

        assertThat(detalle).isNotNull();
        assertThat(detalle.id()).isEqualTo(cartillaId);
        assertThat(detalle.creadorId()).isEqualTo(terapeutaId);
        assertThat(detalle.nombre()).isEqualTo("Cartilla A");
        assertThat(detalle.esPrincipal()).isTrue();
        assertThat(detalle.paradigma()).isEqualTo(ParadigmaCartilla.ESQUEMATICA);

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
    @DisplayName("Detalle de cartilla → pictograma null cuando el item no tiene recurso asociado "
            + "(cobertura de rama defensiva; check_origen_recurso + XOR de resolverRecurso hacen "
            + "este estado inalcanzable en producción)")
    void obtenerDetalle_itemSinRecursoAsociado_pictogramaNull() {
        UUID cartillaId = UUID.randomUUID();

        Cartilla cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .creador(terapeuta)
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

        ItemCartilla item = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Fantasma")
                .ordenVisual(0)
                .build();

        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId)).willReturn(List.of(categoria));
        given(itemCartillaRepository.findByCategoriaIdInOrderByOrdenVisualAsc(List.of(categoria.getId())))
                .willReturn(List.of(item));

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
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER))
                .willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartillaService.obtenerCartillaDetalle(pacienteId, cartillaId, "test@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
    }

    // ──────────────────────────────────────────────
    //  ESTABLECER CARTILLA PRINCIPAL (PUT /{cartillaId}/principal)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("establecerCartillaPrincipal: ya es la principal → devuelve el DTO SIN escribir nada (idempotente)")
    void establecerCartillaPrincipal_yaPrincipal_noEscribe() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder().id(cartillaId).paciente(paciente).creador(terapeuta)
                .nombre("A").esPrincipal(true).build();
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER)).willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));

        CartillaResponseDTO r = cartillaService.establecerCartillaPrincipal(pacienteId, cartillaId, "test@ejemplo.com");

        assertThat(r.id()).isEqualTo(cartillaId);
        assertThat(r.esPrincipal()).isTrue();
        verify(cartillaRepository, never()).desmarcarOtrasPrincipalesDe(any(), any());
        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("establecerCartillaPrincipal: desmarca las otras ANTES de marcar y guardar la elegida")
    void establecerCartillaPrincipal_desmarcaAntesDeGuardar() {
        UUID cartillaId = UUID.randomUUID();
        Cartilla cartilla = Cartilla.builder().id(cartillaId).paciente(paciente).creador(terapeuta)
                .nombre("B").esPrincipal(false).build();
        given(usuarioRepository.findByEmail("test@ejemplo.com")).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER)).willReturn(accesoDeEquipo());
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)).willReturn(Optional.of(cartilla));
        given(cartillaRepository.save(any(Cartilla.class))).willAnswer(i -> i.getArgument(0));

        CartillaResponseDTO r = cartillaService.establecerCartillaPrincipal(pacienteId, cartillaId, "test@ejemplo.com");

        assertThat(r.esPrincipal()).isTrue();
        InOrder orden = inOrder(cartillaRepository);
        orden.verify(cartillaRepository).desmarcarOtrasPrincipalesDe(pacienteId, cartillaId);
        orden.verify(cartillaRepository).save(cartilla);
    }

    @Test
    @DisplayName("establecerCartillaPrincipal: un familiar vinculado → AccesoDenegadoException y no toca la base")
    void establecerCartillaPrincipal_familiar_403() {
        Usuario familiar = Usuario.builder().id(UUID.randomUUID()).email("f@ejemplo.com").build();
        UUID cartillaId = UUID.randomUUID();
        given(usuarioRepository.findByEmail("f@ejemplo.com")).willReturn(Optional.of(familiar));
        given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER))
                .willReturn(accesoFamiliar(PermisoColaborador.EDICION_LIMITADA));

        assertThatThrownBy(() -> cartillaService.establecerCartillaPrincipal(pacienteId, cartillaId, "f@ejemplo.com"))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("equipo");
        verify(cartillaRepository, never()).findByIdAndPacienteId(any(), any());
        verify(cartillaRepository, never()).desmarcarOtrasPrincipalesDe(any(), any());
        verify(cartillaRepository, never()).save(any());
    }
}
