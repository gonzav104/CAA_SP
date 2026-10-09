package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.caa.api.dtos.PictogramaCustomActualizacionDTO;
import com.caa.api.dtos.PictogramaCustomRegistroDTO;
import com.caa.api.dtos.PictogramaCustomResponseDTO;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.RolGestion;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.impl.PictogramaCustomServiceImpl;
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
import org.springframework.mock.web.MockMultipartFile;

/**
 * CRUD de PictogramaCustom con autorización vía {@code AccesoService}: GET (LEER), POST/PUT
 * (EDITAR_CONTENIDO), DELETE (GESTION_CLINICA) — migrado desde la fachada
 * {@code PacienteService.pacienteLegibleParaUsuario}/{@code verificarEdicionParaUsuario} per
 * design-part2 §11.2 (tareas 3.5, Fase 3 continuación). {@code eliminarPictograma} ya migró en
 * el commit {@code bf8267a}; list/get/create/update migran en esta sesión, siguiendo el mismo
 * idiom de {@code CartillaServiceTest}/{@code CategoriaServiceTest}/{@code ItemCartillaServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PictogramaCustomService — CRUD y autorización vía AccesoService")
class PictogramaCustomServiceTest {

    @Mock PictogramaCustomRepository pictogramaCustomRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ItemCartillaRepository itemCartillaRepository;
    @Mock CloudinaryService cloudinaryService;
    @Mock AccesoService accesoService;

    @InjectMocks PictogramaCustomServiceImpl pictogramaCustomService;

    private UUID pacienteId;
    private UUID pictogramaId;
    private UUID organizacionId;
    private Usuario terapeuta;
    private Organizacion organizacion;
    private Paciente paciente;
    private PictogramaCustom pictograma;
    private MockMultipartFile imagen;
    private static final String URL_IMAGEN = "https://res.cloudinary.com/test/upload/caa-sp/pacientes/x/pictogramas/foto.png";

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        pictogramaId = UUID.randomUUID();
        organizacionId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(UUID.randomUUID())
                .email("terapeuta@test.com")
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();

        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .organizacion(organizacion)
                .nombre("Nico")
                .apellido("Perez")
                .build();

        pictograma = PictogramaCustom.builder()
                .id(pictogramaId)
                .paciente(paciente)
                .etiqueta("Mi foto")
                .imagenUrl(URL_IMAGEN)
                .build();

        imagen = new MockMultipartFile("archivo", "foto.png", "image/png", new byte[]{1, 2, 3});
    }

    /** Fixture para el acceso de equipo mockeado de {@code AccesoService} (tarea 3.5). */
    private AccesoPaciente accesoDeEquipo() {
        Membresia membresia = Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeuta.getId()))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build();
        return new AccesoPaciente(paciente, membresia, false, null);
    }

    @Test
    @DisplayName("GET lista → miembro de equipo ve los pictogramas del paciente")
    void obtenerLista_terapeuta_losVe() {
        given(usuarioRepository.findByEmail(terapeuta.getEmail())).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER))
                .willReturn(accesoDeEquipo());
        given(pictogramaCustomRepository.findByPaciente_Id(pacienteId)).willReturn(List.of(pictograma));

        List<PictogramaCustomResponseDTO> resultado =
                pictogramaCustomService.obtenerPictogramasDePaciente(pacienteId, terapeuta.getEmail());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).etiqueta()).isEqualTo("Mi foto");
        assertThat(resultado.get(0).imagenUrl()).isEqualTo(URL_IMAGEN);
        verify(accesoService).exigirCapacidad(pacienteId, terapeuta, Capacidad.LEER);
    }

    @Test
    @DisplayName("GET lista → familiar SIN vínculo con el paciente → 404 (no tiene acceso)")
    void obtenerLista_familiarSinVinculo_noVe() {
        Usuario familiar = Usuario.builder()
                .id(UUID.randomUUID())
                .email("familiar@test.com")
                .nombre("Mama")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail(familiar.getEmail())).willReturn(Optional.of(familiar));
        org.mockito.BDDMockito.willThrow(new RecursoNoEncontradoException("No tiene acceso a este paciente"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.LEER);

        assertThatThrownBy(() -> pictogramaCustomService.obtenerPictogramasDePaciente(pacienteId, familiar.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("No tiene acceso");
        verify(pictogramaCustomRepository, never()).findByPaciente_Id(any());
    }

    @Test
    @DisplayName("POST → miembro de equipo crea el pictograma con la URL devuelta por Cloudinary")
    void crear_terapeuta_creaConUrlCloudinary() {
        given(usuarioRepository.findByEmail(terapeuta.getEmail())).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO))
                .willReturn(accesoDeEquipo());
        given(cloudinaryService.subirImagen(imagen, pacienteId)).willReturn(URL_IMAGEN);

        PictogramaCustom guardado = PictogramaCustom.builder()
                .id(pictogramaId)
                .paciente(paciente)
                .etiqueta("Mi foto")
                .imagenUrl(URL_IMAGEN)
                .build();
        given(pictogramaCustomRepository.save(any(PictogramaCustom.class))).willReturn(guardado);

        PictogramaCustomRegistroDTO dto = new PictogramaCustomRegistroDTO("Mi foto");
        PictogramaCustomResponseDTO resultado =
                pictogramaCustomService.crearPictograma(pacienteId, dto, imagen, terapeuta.getEmail());

        assertThat(resultado.etiqueta()).isEqualTo("Mi foto");
        assertThat(resultado.imagenUrl()).isEqualTo(URL_IMAGEN);
        verify(cloudinaryService).subirImagen(imagen, pacienteId);
        verify(accesoService).exigirCapacidad(pacienteId, terapeuta, Capacidad.EDITAR_CONTENIDO);
    }

    @Test
    @DisplayName("POST → familiar LECTURA no puede crear → 404 (sin permiso de edición)")
    void crear_familiarLectura_noPuede() {
        Usuario familiar = Usuario.builder()
                .id(UUID.randomUUID())
                .email("familiar@test.com")
                .nombre("Mama")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail(familiar.getEmail())).willReturn(Optional.of(familiar));
        org.mockito.BDDMockito.willThrow(new RecursoNoEncontradoException("No tiene permisos de edición"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.EDITAR_CONTENIDO);

        assertThatThrownBy(() -> pictogramaCustomService.crearPictograma(
                pacienteId, new PictogramaCustomRegistroDTO("Mi foto"), imagen, familiar.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(cloudinaryService, never()).subirImagen(any(), any());
    }

    @Test
    @DisplayName("PUT → actualiza etiqueta y sube imagen nueva si viene archivo")
    void actualizar_conArchivo_cambiaEtiquetaYUrl() {
        given(usuarioRepository.findByEmail(terapeuta.getEmail())).willReturn(Optional.of(terapeuta));
        given(pictogramaCustomRepository.findById(pictogramaId)).willReturn(Optional.of(pictograma));
        String nuevaUrl = "https://res.cloudinary.com/test/upload/nueva.png";
        given(cloudinaryService.subirImagen(imagen, pacienteId)).willReturn(nuevaUrl);
        given(pictogramaCustomRepository.save(any(PictogramaCustom.class))).willReturn(pictograma);

        PictogramaCustomActualizacionDTO dto = new PictogramaCustomActualizacionDTO("Foto nueva");
        PictogramaCustomResponseDTO resultado =
                pictogramaCustomService.actualizarPictograma(pacienteId, pictogramaId, dto, imagen, terapeuta.getEmail());

        assertThat(resultado.etiqueta()).isEqualTo("Foto nueva");
        assertThat(resultado.imagenUrl()).isEqualTo(nuevaUrl);
    }

    @Test
    @DisplayName("PUT → sin archivo conserva la imagen existente y solo actualiza etiqueta")
    void actualizar_sinArchivo_conservaUrl() {
        given(usuarioRepository.findByEmail(terapeuta.getEmail())).willReturn(Optional.of(terapeuta));
        given(pictogramaCustomRepository.findById(pictogramaId)).willReturn(Optional.of(pictograma));
        given(pictogramaCustomRepository.save(any(PictogramaCustom.class))).willReturn(pictograma);

        PictogramaCustomActualizacionDTO dto = new PictogramaCustomActualizacionDTO("Solo etiqueta");
        PictogramaCustomResponseDTO resultado =
                pictogramaCustomService.actualizarPictograma(pacienteId, pictogramaId, dto, null, terapeuta.getEmail());

        assertThat(resultado.etiqueta()).isEqualTo("Solo etiqueta");
        assertThat(resultado.imagenUrl()).isEqualTo(URL_IMAGEN);
        verify(cloudinaryService, never()).subirImagen(any(), any());
    }

    @Test
    @DisplayName("PUT → etiqueta en blanco se ignora y conserva la actual")
    void actualizar_etiquetaBlanca_ignoraEtiqueta() {
        given(usuarioRepository.findByEmail(terapeuta.getEmail())).willReturn(Optional.of(terapeuta));
        given(pictogramaCustomRepository.findById(pictogramaId)).willReturn(Optional.of(pictograma));
        given(pictogramaCustomRepository.save(any(PictogramaCustom.class))).willReturn(pictograma);

        PictogramaCustomActualizacionDTO dto = new PictogramaCustomActualizacionDTO("  ");
        PictogramaCustomResponseDTO resultado =
                pictogramaCustomService.actualizarPictograma(pacienteId, pictogramaId, dto, null, terapeuta.getEmail());

        assertThat(resultado.etiqueta()).isEqualTo("Mi foto");
        assertThat(resultado.imagenUrl()).isEqualTo(URL_IMAGEN);
    }

    @Test
    @DisplayName("DELETE → solo terapeuta puede eliminar; familiar → 404")
    void eliminar_familiar_noPuede() {
        Usuario familiar = Usuario.builder()
                .id(UUID.randomUUID())
                .email("familiar@test.com")
                .nombre("Mama")
                .rol(RolUsuario.FAMILIAR)
                .build();

        given(usuarioRepository.findByEmail(familiar.getEmail())).willReturn(Optional.of(familiar));
        org.mockito.BDDMockito.willThrow(new RecursoNoEncontradoException("Paciente no encontrado o no tiene permisos"))
                .given(accesoService).exigirCapacidad(pacienteId, familiar, Capacidad.GESTION_CLINICA);

        assertThatThrownBy(() -> pictogramaCustomService.eliminarPictograma(pacienteId, pictogramaId, familiar.getEmail()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no tiene permisos");
        verify(pictogramaCustomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE → principal inexistente → 404 genérico \"Usuario no encontrado\"")
    void eliminar_principalInexistente_lanzaExcepcion() {
        given(usuarioRepository.findByEmail("nadie@ejemplo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> pictogramaCustomService.eliminarPictograma(pacienteId, pictogramaId, "nadie@ejemplo.com"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
        verify(accesoService, never()).exigirCapacidad(any(), any(), any());
    }

    @Test
    @DisplayName("DELETE → pictograma en uso en un ítem → IllegalArgumentException (400, no se borra)")
    void eliminar_enUso_lanza400() {
        given(usuarioRepository.findByEmail(terapeuta.getEmail())).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(pictogramaCustomRepository.findById(pictogramaId)).willReturn(Optional.of(pictograma));
        given(itemCartillaRepository.existsByRecursoCustomId(pictogramaId)).willReturn(true);

        assertThatThrownBy(() -> pictogramaCustomService.eliminarPictograma(pacienteId, pictogramaId, terapeuta.getEmail()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("está en uso en al menos un ítem");
        verify(pictogramaCustomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE → equipo de gestión y sin uso → elimina el pictograma")
    void eliminar_terapeutaSinUso_elimina() {
        given(usuarioRepository.findByEmail(terapeuta.getEmail())).willReturn(Optional.of(terapeuta));
        given(accesoService.exigirCapacidad(pacienteId, terapeuta, Capacidad.GESTION_CLINICA))
                .willReturn(accesoDeEquipo());
        given(pictogramaCustomRepository.findById(pictogramaId)).willReturn(Optional.of(pictograma));
        given(itemCartillaRepository.existsByRecursoCustomId(pictogramaId)).willReturn(false);

        pictogramaCustomService.eliminarPictograma(pacienteId, pictogramaId, terapeuta.getEmail());

        verify(pictogramaCustomRepository).delete(pictograma);
    }
}