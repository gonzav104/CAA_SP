package com.caa.api.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.PictogramaGlobal;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestpicmat;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("PictogramaGlobal — POST /api/pictogramas-globales/materializar")
class PictogramaGlobalMaterializarIntegrationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final long ARASAAC_ID_NUEVO = 12345L;
    private static final String ETIQUETA_NUEVA = "pelota";
    private static final String IMAGEN_ESPERADA =
            "https://static.arasaac.org/pictograms/12345/12345_300.png";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PictogramaGlobalRepository pictogramaGlobalRepository;

    private MockMvc mockMvc;
    private String tokenValido;
    private Usuario terapeuta;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        terapeuta = Usuario.builder()
                .id(UUID.randomUUID())
                .email("terapeuta@test.com")
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        tokenValido = jwtService.generarToken(terapeuta);
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
    }

    @Test
    @DisplayName("POST materializar con arasaacId nuevo → 201 con UUID, imagenUrl y arasaacId")
    void materializar_arasaacIdNuevo_devuelve201ConUuid() throws Exception {
        UUID uuidEsperado = UUID.randomUUID();
        PictogramaGlobal guardado = PictogramaGlobal.builder()
                .id(uuidEsperado)
                .etiqueta(ETIQUETA_NUEVA)
                .imagenUrl(IMAGEN_ESPERADA)
                .arasaacId(ARASAAC_ID_NUEVO)
                .build();

        given(pictogramaGlobalRepository.findByArasaacId(ARASAAC_ID_NUEVO))
                .willReturn(Optional.empty());
        given(pictogramaGlobalRepository.save(org.mockito.ArgumentMatchers.any(PictogramaGlobal.class)))
                .willReturn(guardado);

        mockMvc.perform(post("/api/pictogramas-globales/materializar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("arasaacId", ARASAAC_ID_NUEVO, "etiqueta", ETIQUETA_NUEVA)))
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(uuidEsperado.toString()))
                .andExpect(jsonPath("$.etiqueta").value(ETIQUETA_NUEVA))
                .andExpect(jsonPath("$.imagenUrl").value(IMAGEN_ESPERADA))
                .andExpect(jsonPath("$.arasaacId").value(ARASAAC_ID_NUEVO));
    }

    @Test
    @DisplayName("POST materializar con arasaacId duplicado → 200 con el EXISTENTE (idempotente, sin duplicar)")
    void materializar_arasaacIdDuplicado_devuelve200Existente() throws Exception {
        UUID uuidExistente = UUID.randomUUID();
        PictogramaGlobal existente = PictogramaGlobal.builder()
                .id(uuidExistente)
                .etiqueta("pelota vieja")
                .imagenUrl(IMAGEN_ESPERADA)
                .arasaacId(ARASAAC_ID_NUEVO)
                .build();

        given(pictogramaGlobalRepository.findByArasaacId(ARASAAC_ID_NUEVO))
                .willReturn(Optional.of(existente));

        mockMvc.perform(post("/api/pictogramas-globales/materializar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("arasaacId", ARASAAC_ID_NUEVO, "etiqueta", "pelota nueva")))
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(uuidExistente.toString()))
                .andExpect(jsonPath("$.etiqueta").value("pelota vieja"));

        verify(pictogramaGlobalRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("POST materializar SIN token → 401")
    void materializar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/pictogramas-globales/materializar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("arasaacId", ARASAAC_ID_NUEVO, "etiqueta", ETIQUETA_NUEVA))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST materializar con arasaacId = 0 → 400 (validación @Min(1))")
    void materializar_arasaacIdCero_devuelve400() throws Exception {
        mockMvc.perform(post("/api/pictogramas-globales/materializar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("arasaacId", 0, "etiqueta", ETIQUETA_NUEVA)))
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST materializar con arasaacId negativo → 400 (validación @Min(1))")
    void materializar_arasaacIdNegativo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/pictogramas-globales/materializar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("arasaacId", -5, "etiqueta", ETIQUETA_NUEVA)))
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST materializar con etiqueta vacía → 400 (validación @NotBlank)")
    void materializar_etiquetaVacia_devuelve400() throws Exception {
        mockMvc.perform(post("/api/pictogramas-globales/materializar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("arasaacId", ARASAAC_ID_NUEVO, "etiqueta", "")))
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST materializar sin etiqueta → 400 (validación @NotBlank)")
    void materializar_sinEtiqueta_devuelve400() throws Exception {
        mockMvc.perform(post("/api/pictogramas-globales/materializar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("arasaacId", ARASAAC_ID_NUEVO)))
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET pictogramas-globales incluye arasaacId en el DTO")
    void obtenerTodos_incluyeArasaacId() throws Exception {
        PictogramaGlobal pictograma = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("agua")
                .imagenUrl("https://static.arasaac.org/pictograms/32464/32464_300.png")
                .arasaacId(32464L)
                .build();

        given(pictogramaGlobalRepository.findAll())
                .willReturn(java.util.List.of(pictograma));

        mockMvc.perform(get("/api/pictogramas-globales")
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].arasaacId").value(32464));
    }

    @Test
    @DisplayName("GET pictogramas-globales con pictograma sin arasaacId (legacy) → arasaacId null")
    void obtenerTodos_pictogramaSinArasaacId_arasaacIdNull() throws Exception {
        PictogramaGlobal pictograma = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("legacy")
                .imagenUrl("https://example.com/img.png")
                .arasaacId(null)
                .build();

        given(pictogramaGlobalRepository.findAll())
                .willReturn(java.util.List.of(pictograma));

        mockMvc.perform(get("/api/pictogramas-globales")
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].arasaacId").isEmpty());
    }
}
