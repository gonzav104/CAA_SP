package com.caa.api.services;

import com.caa.api.dtos.CategoriaActualizacionDTO;
import com.caa.api.dtos.ItemCartillaActualizacionDTO;
import com.caa.api.dtos.ItemCartillaResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PacienteTerapeutaId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.RolGestion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PacienteTerapeutaRepository;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.CartillaServiceImpl;
import com.caa.api.services.impl.CategoriaServiceImpl;
import com.caa.api.services.impl.ItemCartillaServiceImpl;
import com.caa.api.services.impl.AccesoServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Autorización basada en rol FAMILIAR: lectura de pacientes asignados (vía PacienteFamiliar)
 * y edición limitada (EDICION_LIMITADA) de categorías/items, pero nunca eliminación ni
 * creación de recursos clínicos.
 */
@DisplayName("Acceso FAMILIAR — autorización por rol y permiso")
class AccesoFamiliarServiceTest {

    // ──────────────────────────────────────────────
    //  Acceso al paciente (AccesoServiceImpl.exigirCapacidad)
    // ──────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("AccesoServiceImpl — exigirCapacidad para terapeuta y familiar")
    class PacienteAccesoTest {

        @Mock MembresiaRepository membresiaRepository;
        @Mock PacienteRepository pacienteRepository;
        @Mock PacienteTerapeutaRepository pacienteTerapeutaRepository;
        @Mock PacienteFamiliarRepository pacienteFamiliarRepository;

        @InjectMocks AccesoServiceImpl accesoService;

        private UUID organizacionId;
        private UUID pacienteId;
        private Organizacion organizacion;
        private Usuario terapeuta;
        private Usuario familiar;
        private Paciente paciente;

        @BeforeEach
        void setUp() {
            organizacionId = UUID.randomUUID();
            pacienteId = UUID.randomUUID();
            organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
            terapeuta = Usuario.builder().id(UUID.randomUUID()).build();
            familiar = Usuario.builder().id(UUID.randomUUID()).build();
            paciente = Paciente.builder().id(pacienteId).organizacion(organizacion).build();
            given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        }

        private void familiarSinMembresiaConPermiso(PermisoColaborador permiso) {
            given(membresiaRepository.findById(new MembresiaId(organizacionId, familiar.getId())))
                    .willReturn(Optional.empty());
            Optional<PacienteFamiliar> vinculo = permiso == null ? Optional.empty()
                    : Optional.of(PacienteFamiliar.builder()
                            .id(new PacienteFamiliarId(pacienteId, familiar.getId()))
                            .paciente(paciente).usuario(familiar).permiso(permiso).build());
            given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiar.getId()))
                    .willReturn(vinculo);
        }

        @Test
        @DisplayName("Terapeuta asignado → lee el paciente (exigirCapacidad(LEER))")
        void terapeutaVeSuPaciente() {
            Membresia membresia = Membresia.builder()
                    .id(new MembresiaId(organizacionId, terapeuta.getId()))
                    .organizacion(organizacion).usuario(terapeuta)
                    .rolGestion(RolGestion.MIEMBRO).esTerapeuta(true).build();
            given(membresiaRepository.findById(new MembresiaId(organizacionId, terapeuta.getId())))
                    .willReturn(Optional.of(membresia));
            given(pacienteTerapeutaRepository.existsById(new PacienteTerapeutaId(pacienteId, terapeuta.getId())))
                    .willReturn(true);

            Paciente resultado = accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER).paciente();

            assertThat(resultado.getId()).isEqualTo(pacienteId);
        }

        @Test
        @DisplayName("Familiar vinculado → lee el paciente")
        void familiarVinculadoPuedeLeer() {
            familiarSinMembresiaConPermiso(PermisoColaborador.LECTURA);

            Paciente resultado = accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER).paciente();

            assertThat(resultado.getId()).isEqualTo(pacienteId);
        }

        @Test
        @DisplayName("Familiar sin vínculo → RecursoNoEncontradoException genérica")
        void familiarSinVinculoNoPuedeLeer() {
            familiarSinMembresiaConPermiso(null);

            assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("no tiene permisos");
        }

        @Test
        @DisplayName("Familiar con LECTURA puede leer pero NO editar")
        void familiarLecturaNoPuedeEditar() {
            familiarSinMembresiaConPermiso(PermisoColaborador.LECTURA);

            // Lee: ok
            assertThatCode(() -> accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER))
                    .doesNotThrowAnyException();

            // Edita: lanza
            assertThatThrownBy(() -> accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("no tiene permisos");
        }

        @Test
        @DisplayName("Familiar con EDICION_LIMITADA puede leer y editar")
        void familiarEdicionLimitadaPuedeEditar() {
            familiarSinMembresiaConPermiso(PermisoColaborador.EDICION_LIMITADA);

            assertThatCode(() -> accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER))
                    .doesNotThrowAnyException();
            assertThatCode(() -> accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────
    //  CartillaServiceImpl — GET público
    // ──────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("CartillaServiceImpl — lectura")
    class CartillaLecturaTest {

        @Mock CartillaRepository cartillaRepository;
        @Mock UsuarioRepository usuarioRepository;
        @Mock AccesoService accesoService;

        @InjectMocks CartillaServiceImpl cartillaService;

        private UUID pacienteId;
        private Paciente paciente;
        private Usuario familiar;

        @BeforeEach
        void setUp() {
            pacienteId = UUID.randomUUID();
            familiar = Usuario.builder().id(UUID.randomUUID()).build();
            paciente = Paciente.builder().id(pacienteId).build();
        }

        @Test
        @DisplayName("Familiar vinculado puede obtener las cartillas (GET) con creadorId del creador")
        void familiarPuedeObtenerCartillas() {
            Cartilla c = Cartilla.builder().id(UUID.randomUUID()).paciente(paciente).creador(familiar)
                    .nombre("A").esPrincipal(true).build();
            given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
            given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.LEER))
                    .willReturn(new AccesoPaciente(paciente, null, false, PermisoColaborador.LECTURA));
            given(cartillaRepository.findByPacienteId(pacienteId)).willReturn(List.of(c));

            var resultado = cartillaService.obtenerCartillasDePaciente(pacienteId, "familiar@ejemplo.com");

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).creadorId()).isEqualTo(familiar.getId());
            verify(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.LEER);
        }
    }

    // ──────────────────────────────────────────────
    //  CategoriaServiceImpl — GET público, PUT con permiso, DELETE solo terapeuta
    // ──────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("CategoriaServiceImpl — acceso FAMILIAR (EDITAR_CONTENIDO) vs sin acceso")
    class CategoriaAccesoTest {

        @Mock CategoriaRepository categoriaRepository;
        @Mock CartillaRepository cartillaRepository;
        @Mock UsuarioRepository usuarioRepository;
        @Mock AccesoService accesoService;

        @InjectMocks CategoriaServiceImpl categoriaService;

        private UUID pacienteId;
        private UUID cartillaId;
        private UUID categoriaId;
        private Paciente paciente;
        private Cartilla cartilla;
        private Categoria categoria;

        @BeforeEach
        void setUp() {
            pacienteId = UUID.randomUUID();
            cartillaId = UUID.randomUUID();
            categoriaId = UUID.randomUUID();
            paciente = Paciente.builder().id(pacienteId).build();
            cartilla = Cartilla.builder().id(cartillaId).paciente(paciente).build();
            categoria = Categoria.builder().id(categoriaId).cartilla(cartilla).nombre("Acciones").colorHex("#00FF00").build();
        }

        @Test
        @DisplayName("Familiar con EDICION_LIMITADA puede PUT la categoría (acceso deriva del paciente, no del creador)")
        void familiarEdicionLimitadaPuedeActualizarCategoria() {
            Usuario familiar = Usuario.builder().id(UUID.randomUUID()).build();
            given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
            given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                    .willReturn(new AccesoPaciente(paciente, null, false, PermisoColaborador.EDICION_LIMITADA));
            given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                    .willReturn(Optional.of(cartilla));
            given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                    .willReturn(Optional.of(categoria));
            given(categoriaRepository.save(any(Categoria.class))).willReturn(categoria);

            CategoriaActualizacionDTO dto = new CategoriaActualizacionDTO("Nuevo", "#FF0000", 2);
            var response = categoriaService.actualizarCategoria(pacienteId, cartillaId, categoriaId, dto, "familiar@ejemplo.com");

            assertThat(response.nombre()).isEqualTo("Nuevo");
        }

        @Test
        @DisplayName("Familiar sin acceso de EDITAR_CONTENIDO no puede eliminar la categoría (404 genérico)")
        void familiarSinAccesoNoPuedeEliminarCategoria() {
            Usuario familiar = Usuario.builder().id(UUID.randomUUID()).build();
            given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
            given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                    .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

            assertThatThrownBy(() ->
                    categoriaService.eliminarCategoria(pacienteId, cartillaId, categoriaId, "familiar@ejemplo.com"))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(categoriaRepository, never()).delete(any());
        }
    }

    // ──────────────────────────────────────────────
    //  ItemCartillaServiceImpl — PUT con permiso, DELETE solo terapeuta
    // ──────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("ItemCartillaServiceImpl — acceso FAMILIAR (EDITAR_CONTENIDO) vs sin acceso")
    class ItemAccesoTest {

        @Mock ItemCartillaRepository itemCartillaRepository;
        @Mock CategoriaRepository categoriaRepository;
        @Mock CartillaRepository cartillaRepository;
        @Mock UsuarioRepository usuarioRepository;
        @Mock PictogramaGlobalRepository pictogramaGlobalRepository;
        @Mock PictogramaCustomRepository pictogramaCustomRepository;
        @Mock AccesoService accesoService;

        @InjectMocks ItemCartillaServiceImpl itemCartillaService;

        private UUID pacienteId;
        private UUID cartillaId;
        private UUID categoriaId;
        private UUID itemId;
        private Paciente paciente;
        private Cartilla cartilla;
        private Categoria categoria;

        @BeforeEach
        void setUp() {
            pacienteId = UUID.randomUUID();
            cartillaId = UUID.randomUUID();
            categoriaId = UUID.randomUUID();
            itemId = UUID.randomUUID();
            paciente = Paciente.builder().id(pacienteId).build();
            cartilla = Cartilla.builder().id(cartillaId).paciente(paciente).build();
            categoria = Categoria.builder().id(categoriaId).cartilla(cartilla).nombre("Acciones").build();
        }

        @Test
        @DisplayName("Familiar con EDICION_LIMITADA puede PUT el item (acceso deriva del paciente, no del creador)")
        void familiarEdicionLimitadaPuedeActualizarItem() {
            Usuario familiar = Usuario.builder().id(UUID.randomUUID()).build();
            UUID globalId = UUID.randomUUID();
            PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();

            given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
            given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                    .willReturn(new AccesoPaciente(paciente, null, false, PermisoColaborador.EDICION_LIMITADA));
            given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                    .willReturn(Optional.of(cartilla));
            given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                    .willReturn(Optional.of(categoria));
            ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria).build();
            given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId)).willReturn(Optional.of(item));
            given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

            ItemCartilla guardado = ItemCartilla.builder().id(itemId).categoria(categoria).textoHablado("Hola")
                    .ordenVisual(1).recursoGlobal(global).build();
            given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

            ItemCartillaActualizacionDTO dto = new ItemCartillaActualizacionDTO("Hola", 1, globalId, null, null, null, null);
            ItemCartillaResponseDTO response = itemCartillaService.actualizarItem(
                    pacienteId, cartillaId, categoriaId, itemId, dto, "familiar@ejemplo.com");

            assertThat(response.textoHablado()).isEqualTo("Hola");
        }

        @Test
        @DisplayName("Familiar sin acceso de EDITAR_CONTENIDO no puede eliminar el item (404 genérico)")
        void familiarSinAccesoNoPuedeEliminarItem() {
            Usuario familiar = Usuario.builder().id(UUID.randomUUID()).build();
            given(usuarioRepository.findByEmail("familiar@ejemplo.com")).willReturn(Optional.of(familiar));
            given(accesoService.exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO))
                    .willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"));

            assertThatThrownBy(() ->
                    itemCartillaService.eliminarItem(pacienteId, cartillaId, categoriaId, itemId, "familiar@ejemplo.com"))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(itemCartillaRepository, never()).delete(any());
        }
    }
}
