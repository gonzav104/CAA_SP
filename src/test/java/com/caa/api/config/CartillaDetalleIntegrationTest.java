package com.caa.api.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Cartilla;
import com.caa.api.models.Categoria;
import com.caa.api.models.ItemCartilla;
import com.caa.api.models.Paciente;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.CategoriaRepository;
import com.caa.api.repositories.ItemCartillaRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestcd;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret"
})
@DisplayName("CartillaDetalle — GET anidado /api/pacientes/{pacienteId}/cartillas/{cartillaId}")
class CartillaDetalleIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PacienteRepository pacienteRepository;

    @MockitoBean
    private CartillaRepository cartillaRepository;

    @MockitoBean
    private CategoriaRepository categoriaRepository;

    @MockitoBean
    private ItemCartillaRepository itemCartillaRepository;

    @MockitoBean
    private PictogramaGlobalRepository pictogramaGlobalRepository;

    @MockitoBean
    private PictogramaCustomRepository pictogramaCustomRepository;

    private MockMvc mockMvc;
    private String tokenValido;
    private UUID pacienteId;
    private UUID cartillaId;
    private Paciente paciente;
    private Usuario terapeuta;
    private Cartilla cartilla;
    private Categoria categoria;
    private PictogramaGlobal pictogramaGlobal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        pacienteId = UUID.randomUUID();
        cartillaId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(UUID.randomUUID())
                .email("terapeuta@test.com")
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .nombre("Nico")
                .apellido("Perez")
                .build();

        cartilla = Cartilla.builder()
                .id(cartillaId)
                .paciente(paciente)
                .nombre("Mi tablero")
                .esPrincipal(true)
                .build();

        categoria = Categoria.builder()
                .id(UUID.randomUUID())
                .cartilla(cartilla)
                .nombre("Acciones")
                .colorHex("#00BFFF")
                .orden(0)
                .build();

        pictogramaGlobal = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("Correr")
                .imagenUrl("http://img/correr.png")
                .build();

        tokenValido = jwtService.generarToken(terapeuta);
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId()))
                .willReturn(Optional.of(paciente));
    }

    @Test
    @DisplayName("GET detalle autenticado → 200 con shape anidado y pictograma GLOBAL")
    void getDetalle_tokenValido_devuelve200Anidado() throws Exception {
        ItemCartilla item = ItemCartilla.builder()
                .id(UUID.randomUUID())
                .categoria(categoria)
                .textoHablado("Correr")
                .ordenVisual(0)
                .recursoGlobal(pictogramaGlobal)
                .build();

        given(cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId))
                .willReturn(Optional.of(cartilla));
        given(categoriaRepository.findByCartillaIdOrderByOrdenAsc(cartillaId))
                .willReturn(List.of(categoria));
        given(itemCartillaRepository.findByCategoriaIdOrderByOrdenVisualAsc(categoria.getId()))
                .willReturn(List.of(item));
        given(pictogramaGlobalRepository.findById(pictogramaGlobal.getId()))
                .willReturn(Optional.of(pictogramaGlobal));

        mockMvc.perform(get("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartillaId.toString()))
                .andExpect(jsonPath("$.nombre").value("Mi tablero"))
                .andExpect(jsonPath("$.esPrincipal").value(true))
                .andExpect(jsonPath("$.categorias[0].id").value(categoria.getId().toString()))
                .andExpect(jsonPath("$.categorias[0].nombre").value("Acciones"))
                .andExpect(jsonPath("$.categorias[0].colorHex").value("#00BFFF"))
                .andExpect(jsonPath("$.categorias[0].orden").value(0))
                .andExpect(jsonPath("$.categorias[0].items[0].textoHablado").value("Correr"))
                .andExpect(jsonPath("$.categorias[0].items[0].ordenVisual").value(0))
                .andExpect(jsonPath("$.categorias[0].items[0].pictograma.tipo").value("GLOBAL"))
                .andExpect(jsonPath("$.categorias[0].items[0].pictograma.etiqueta").value("Correr"))
                .andExpect(jsonPath("$.categorias[0].items[0].pictograma.imagenUrl").value("http://img/correr.png"));
    }

    @Test
    @DisplayName("GET detalle autenticado pero cartilla inexistente → 404")
    void getDetalle_cartillaInexistente_devuelve404() throws Exception {
        UUID cartillaInvalida = UUID.randomUUID();
        given(cartillaRepository.findByIdAndPacienteId(cartillaInvalida, pacienteId))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaInvalida)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET detalle SIN token → 401 (el endpoint NO es público)")
    void getDetalle_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes/{pacienteId}/cartillas/{cartillaId}", pacienteId, cartillaId))
                .andExpect(status().isUnauthorized());
    }
}