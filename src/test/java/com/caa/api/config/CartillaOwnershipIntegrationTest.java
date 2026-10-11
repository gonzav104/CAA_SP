package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.ParadigmaCartilla;
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
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Acceso a cartillas vía el paciente (no vía {@code creador_id}) demostrado vía HTTP, migrado a
 * {@code AccesoService} (tarea 3.3, design-part2 §11.2, R2):
 *  - crear: gate de acceso (EDITAR_CONTENIDO) → queda .creador(usuario)
 *  - escribir (PUT/DELETE cartilla + categorías + items): cualquier miembro de equipo o familiar
 *    con EDICION_LIMITADA, SIN importar quién creó esa cartilla
 *  - escenario 7: un miembro de equipo puede tocar una cartilla creada por un familiar
 *  - escenario 9: dos creadores distintos pueden marcar su cartilla como principal
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caaownership;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("Cartillas — acceso vía AccesoService (HTTP)")
class CartillaOwnershipIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PacienteRepository pacienteRepository;

    @MockitoBean
    private PacienteFamiliarRepository pacienteFamiliarRepository;

    @MockitoBean
    private MembresiaRepository membresiaRepository;

    @MockitoBean
    private PacienteTerapeutaRepository pacienteTerapeutaRepository;

    @MockitoBean
    private CartillaRepository cartillaRepository;

    @MockitoBean
    private CategoriaRepository categoriaRepository;

    @MockitoBean
    private ItemCartillaRepository itemCartillaRepository;

    @MockitoBean
    private PictogramaGlobalRepository pictogramaGlobalRepository;

    private MockMvc mockMvc;
    private UUID pacienteId;
    private UUID organizacionId;
    private UUID cartillaId;
    private UUID categoriaId;
    private Usuario terapeuta;
    private Usuario familiarEdicion;
    private Usuario familiarLectura;
    private Organizacion organizacion;
    private Paciente paciente;
    private Cartilla cartillaDelTerapeuta;
    private Categoria categoria;
    private String tokenTerapeuta;
    private String tokenFamiliarEdicion;
    private String tokenFamiliarLectura;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        pacienteId = UUID.randomUUID();
        organizacionId = UUID.randomUUID();
        cartillaId = UUID.randomUUID();
        categoriaId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(UUID.randomUUID()).email("terapeuta@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta").build();
        familiarEdicion = Usuario.builder()
                .id(UUID.randomUUID()).email("edicion@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Familiar Edicion").build();
        familiarLectura = Usuario.builder()
                .id(UUID.randomUUID()).email("lectura@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Familiar Lectura").build();

        organizacion = Organizacion.builder().id(organizacionId).nombre("Consultorio").build();
        paciente = Paciente.builder()
                .id(pacienteId).organizacion(organizacion)
                .nombre("Nico").apellido("Perez").build();

        cartillaDelTerapeuta = Cartilla.builder()
                .id(cartillaId).paciente(paciente).creador(terapeuta)
                .nombre("Tablero del terapeuta").esPrincipal(false).build();
        categoria = Categoria.builder()
                .id(categoriaId).cartilla(cartillaDelTerapeuta)
                .nombre("Acciones").colorHex("#00BFFF").orden(0).build();

        tokenTerapeuta = jwtService.generarToken(terapeuta);
        tokenFamiliarEdicion = jwtService.generarToken(familiarEdicion);
        tokenFamiliarLectura = jwtService.generarToken(familiarLectura);

        // Identidad
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        given(usuarioRepository.findByEmail("edicion@test.com")).willReturn(Optional.of(familiarEdicion));
        given(usuarioRepository.findByEmail("lectura@test.com")).willReturn(Optional.of(familiarLectura));

        // Helpers de acceso (AccesoServiceImpl real)
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        Membresia terapeutaOwner = Membresia.builder()
                .id(new MembresiaId(organizacionId, terapeuta.getId()))
                .organizacion(organizacion)
                .usuario(terapeuta)
                .rolGestion(RolGestion.OWNER)
                .esTerapeuta(true)
                .build();
        given(membresiaRepository.findById(new MembresiaId(organizacionId, terapeuta.getId())))
                .willReturn(Optional.of(terapeutaOwner));
        given(membresiaRepository.findById(new MembresiaId(organizacionId, familiarEdicion.getId())))
                .willReturn(Optional.empty());
        given(membresiaRepository.findById(new MembresiaId(organizacionId, familiarLectura.getId())))
                .willReturn(Optional.empty());
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarEdicion.getId()))
                .willReturn(Optional.of(vinculo(familiarEdicion, PermisoColaborador.EDICION_LIMITADA)));
        given(pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, familiarLectura.getId()))
                .willReturn(Optional.of(vinculo(familiarLectura, PermisoColaborador.LECTURA)));

        // Acceso deriva del paciente: la cartilla del terapeuta se resuelve por findByIdAndPacienteId
        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartillaDelTerapeuta));
    }

    private PacienteFamiliar vinculo(Usuario familiar, PermisoColaborador permiso) {
        return PacienteFamiliar.builder()
                .id(new PacienteFamiliarId(pacienteId, familiar.getId()))
                .paciente(paciente)
                .usuario(familiar)
                .permiso(permiso)
                .build();
    }

    // ──────────────────────────────────────────────
    //  CREAR (gate de acceso + .creador(usuario))
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST cartilla: miembro de equipo → 201 y queda como CREADOR")
    void postCartilla_equipoDeGestion_201_quedaCreador() throws Exception {
        Cartilla guardada = Cartilla.builder()
                .id(UUID.randomUUID()).paciente(paciente).creador(terapeuta)
                .nombre("Tablero A").esPrincipal(true).build();
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(guardada);

        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero A\", \"esPrincipal\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Tablero A"))
                .andExpect(jsonPath("$.creadorId").value(terapeuta.getId().toString()))
                .andExpect(jsonPath("$.esPrincipal").value(true));

        var captor = org.mockito.ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getCreador().getId()).isEqualTo(terapeuta.getId());
    }

    @Test
    @DisplayName("POST cartilla: familiar con EDICION_LIMITADA → 201 y queda como CREADOR (el familiar)")
    void postCartilla_familiarEdicion_201_quedaCreador() throws Exception {
        Cartilla guardada = Cartilla.builder()
                .id(UUID.randomUUID()).paciente(paciente).creador(familiarEdicion)
                .nombre("Tablero F").esPrincipal(false).build();
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(guardada);

        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas", pacienteId)
                        .header("Authorization", "Bearer " + tokenFamiliarEdicion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero F\", \"esPrincipal\": false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Tablero F"))
                .andExpect(jsonPath("$.creadorId").value(familiarEdicion.getId().toString()));

        var captor = org.mockito.ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getCreador().getId()).isEqualTo(familiarEdicion.getId());
    }

    @Test
    @DisplayName("POST cartilla: familiar con LECTURA → 404 genérico y NO persiste")
    void postCartilla_familiarLectura_404() throws Exception {
        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas", pacienteId)
                        .header("Authorization", "Bearer " + tokenFamiliarLectura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero X\", \"esPrincipal\": false}"))
                .andExpect(status().isNotFound());

        verify(cartillaRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  PARADIGMA — wire format lowercase (taxonomica/esquematica) y rechazo de escena-visual
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST cartilla: paradigma \"taxonomica\" (lowercase) → 201 y queda TAXONOMICA")
    void postCartilla_paradigmaTaxonomica_201_persisteTaxonomica() throws Exception {
        Cartilla guardada = Cartilla.builder()
                .id(UUID.randomUUID()).paciente(paciente).creador(terapeuta)
                .nombre("Tablero A").esPrincipal(false).paradigma(ParadigmaCartilla.TAXONOMICA).build();
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(guardada);

        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero A\", \"esPrincipal\": false, \"paradigma\": \"taxonomica\"}"))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getParadigma()).isEqualTo(ParadigmaCartilla.TAXONOMICA);
    }

    @Test
    @DisplayName("POST cartilla: paradigma \"esquematica\" (lowercase) → 201 y queda ESQUEMATICA")
    void postCartilla_paradigmaEsquematica_201_persisteEsquematica() throws Exception {
        Cartilla guardada = Cartilla.builder()
                .id(UUID.randomUUID()).paciente(paciente).creador(terapeuta)
                .nombre("Tablero A").esPrincipal(false).paradigma(ParadigmaCartilla.ESQUEMATICA).build();
        given(cartillaRepository.save(any(Cartilla.class))).willReturn(guardada);

        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero A\", \"esPrincipal\": false, \"paradigma\": \"esquematica\"}"))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getParadigma()).isEqualTo(ParadigmaCartilla.ESQUEMATICA);
    }

    @Test
    @DisplayName("POST cartilla: paradigma \"escena-visual\" → 400 (no soportado, NO 500)")
    void postCartilla_paradigmaEscenaVisual_400() throws Exception {
        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero A\", \"esPrincipal\": false, \"paradigma\": \"escena-visual\"}"))
                .andExpect(status().isBadRequest());

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST cartilla: paradigma con string basura → 400 (no soportado, NO 500)")
    void postCartilla_paradigmaBasura_400() throws Exception {
        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero A\", \"esPrincipal\": false, \"paradigma\": \"asdasd\"}"))
                .andExpect(status().isBadRequest());

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT cartilla: paradigma \"esquematica\" (lowercase) → 200 y actualiza a ESQUEMATICA")
    void putCartilla_paradigmaEsquematica_200_actualizaEsquematica() throws Exception {
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero renovado\", \"esPrincipal\": true, \"paradigma\": \"esquematica\"}"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(Cartilla.class);
        verify(cartillaRepository).save(captor.capture());
        assertThat(captor.getValue().getParadigma()).isEqualTo(ParadigmaCartilla.ESQUEMATICA);
    }

    @Test
    @DisplayName("PUT cartilla: paradigma \"escena-visual\" → 400 (no soportado, NO 500)")
    void putCartilla_paradigmaEscenaVisual_400() throws Exception {
        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero renovado\", \"esPrincipal\": true, \"paradigma\": \"escena-visual\"}"))
                .andExpect(status().isBadRequest());

        verify(cartillaRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  PUT/DELETE CARTILLA (gate: acceso al paciente, nunca creador)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT cartilla: miembro de equipo puede actualizarla → 200")
    void putCartilla_equipoDeGestion_200() throws Exception {
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Tablero renovado\", \"esPrincipal\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tablero renovado"))
                .andExpect(jsonPath("$.esPrincipal").value(true));
    }

    @Test
    @DisplayName("PUT cartilla: miembro de equipo puede actualizar una cartilla creada por un FAMILIAR (R2)")
    void putCartilla_equipoDeGestion_200_cartillaCreadaPorFamiliar() throws Exception {
        UUID cartillaDelFamiliarId = UUID.randomUUID();
        Cartilla cartillaDelFamiliar = Cartilla.builder()
                .id(cartillaDelFamiliarId).paciente(paciente).creador(familiarEdicion)
                .nombre("Tablero del familiar").esPrincipal(false).build();
        given(cartillaRepository.findByIdAndPacienteId(cartillaDelFamiliarId, pacienteId))
                .willReturn(Optional.of(cartillaDelFamiliar));
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaDelFamiliarId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Editada por el equipo\", \"esPrincipal\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Editada por el equipo"))
                .andExpect(jsonPath("$.creadorId").value(familiarEdicion.getId().toString()));
    }

    @Test
    @DisplayName("PUT cartilla: familiar con EDICION_LIMITADA puede actualizar aunque NO sea el creador (R2)")
    void putCartilla_familiarEdicion_200_sinSerCreador() throws Exception {
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenFamiliarEdicion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Edicion ajena\", \"esPrincipal\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Edicion ajena"));
    }

    @Test
    @DisplayName("PUT cartilla: familiar con LECTURA (sin EDITAR_CONTENIDO) → 404")
    void putCartilla_familiarLectura_404() throws Exception {
        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenFamiliarLectura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Intento ajeno\", \"esPrincipal\": false}"))
                .andExpect(status().isNotFound());

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT cartilla: cartilla de OTRO paciente → 404")
    void putCartilla_cartillaDeOtroPaciente_404() throws Exception {
        UUID cartillaDeOtroPacienteId = UUID.randomUUID();
        given(cartillaRepository.findByIdAndPacienteId(cartillaDeOtroPacienteId, pacienteId))
                .willReturn(Optional.empty());

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaDeOtroPacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Intento\", \"esPrincipal\": false}"))
                .andExpect(status().isNotFound());

        verify(cartillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("DELETE cartilla: miembro de equipo puede eliminarla → 204")
    void deleteCartilla_equipoDeGestion_204() throws Exception {
        mockMvc.perform(delete("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenTerapeuta))
                .andExpect(status().isNoContent());

        verify(cartillaRepository).delete(cartillaDelTerapeuta);
    }

    @Test
    @DisplayName("DELETE cartilla: familiar (sin GESTION_CLINICA) → 404 y NO elimina")
    void deleteCartilla_familiar_404() throws Exception {
        mockMvc.perform(delete("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenFamiliarEdicion))
                .andExpect(status().isNotFound());

        verify(cartillaRepository, never()).delete(any());
    }

    // ──────────────────────────────────────────────
    //  CATEGORÍAS (gate: acceso al paciente que es dueño de la cartilla)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST categoría: miembro de equipo → 201")
    void postCategoria_equipoDeGestion_201() throws Exception {
        Categoria guardada = Categoria.builder()
                .id(UUID.randomUUID()).cartilla(cartillaDelTerapeuta)
                .nombre("Comida").colorHex("#E0E0E0").orden(0).build();
        given(categoriaRepository.save(any(Categoria.class))).willReturn(guardada);

        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias",
                        pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Comida\", \"colorHex\": \"#E0E0E0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Comida"))
                .andExpect(jsonPath("$.colorHex").value("#E0E0E0"));
    }

    @Test
    @DisplayName("POST categoría: familiar con LECTURA (sin EDITAR_CONTENIDO) → 404")
    void postCategoria_familiarLectura_404() throws Exception {
        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias",
                        pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenFamiliarLectura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Comida\", \"colorHex\": \"#E0E0E0\"}"))
                .andExpect(status().isNotFound());

        verify(categoriaRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  ITEMS (gate: acceso al paciente, antes del XOR)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT item: miembro de equipo → 200")
    void putItem_equipoDeGestion_200() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID globalId = UUID.randomUUID();
        PictogramaGlobal global = PictogramaGlobal.builder().id(globalId).etiqueta("Saludo").build();

        ItemCartilla item = ItemCartilla.builder().id(itemId).categoria(categoria).build();
        given(categoriaRepository.findByIdAndCartillaId(categoriaId, cartillaId))
                .willReturn(Optional.of(categoria));
        given(itemCartillaRepository.findByIdAndCategoriaId(itemId, categoriaId))
                .willReturn(Optional.of(item));
        given(pictogramaGlobalRepository.findById(globalId)).willReturn(Optional.of(global));

        ItemCartilla guardado = ItemCartilla.builder().id(itemId).categoria(categoria)
                .textoHablado("Hola").ordenVisual(1).recursoGlobal(global).build();
        given(itemCartillaRepository.save(any(ItemCartilla.class))).willReturn(guardado);

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias/{categoriaId}/items/{itemId}",
                        pacienteId, cartillaId, categoriaId, itemId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textoHablado\": \"Hola\", \"ordenVisual\": 1, \"recursoGlobalId\": \"" + globalId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoHablado").value("Hola"))
                .andExpect(jsonPath("$.recursoGlobalId").value(globalId.toString()));
    }

    @Test
    @DisplayName("PUT item: familiar con LECTURA (sin EDITAR_CONTENIDO) → 404 ANTES de evaluar el XOR")
    void putItem_familiarLectura_404() throws Exception {
        UUID itemId = UUID.randomUUID();

        // Dto con AMBOS recursos (violaría el XOR), pero el gate de acceso gana antes → 404
        UUID globalId = UUID.randomUUID();
        UUID customId = UUID.randomUUID();

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias/{categoriaId}/items/{itemId}",
                        pacienteId, cartillaId, categoriaId, itemId)
                        .header("Authorization", "Bearer " + tokenFamiliarLectura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textoHablado\": \"Hola\", \"ordenVisual\": 1, \"recursoGlobalId\": \""
                                + globalId + "\", \"recursoCustomId\": \"" + customId + "\"}"))
                .andExpect(status().isNotFound());

        verify(itemCartillaRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  esPrincipal único por paciente: solo un miembro de equipo lo establece
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Miembro de equipo marca principal → 200; familiar con EDICION_LIMITADA intenta lo mismo → 403")
    void soloEquipoDeGestion_estableceLaPrincipal() throws Exception {
        UUID cartillaDelFamiliarId = UUID.randomUUID();
        Cartilla cartillaDelFamiliar = Cartilla.builder()
                .id(cartillaDelFamiliarId).paciente(paciente).creador(familiarEdicion)
                .nombre("Tablero del familiar").esPrincipal(false).build();

        given(cartillaRepository.findByIdAndPacienteId(cartillaDelFamiliarId, pacienteId))
                .willReturn(Optional.of(cartillaDelFamiliar));
        given(cartillaRepository.save(any(Cartilla.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        String body = "{\"nombre\": \"Principal\", \"esPrincipal\": true}";

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esPrincipal").value(true));

        mockMvc.perform(put("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaDelFamiliarId)
                        .header("Authorization", "Bearer " + tokenFamiliarEdicion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
