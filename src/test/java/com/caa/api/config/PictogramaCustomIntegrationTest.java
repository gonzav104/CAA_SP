package com.caa.api.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Paciente;
import com.caa.api.models.PictogramaCustom;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.PictogramaCustomRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.CloudinaryService;
import com.caa.api.services.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestpic;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("PictogramaCustom — POST multipart e integración de seguridad")
class PictogramaCustomIntegrationTest {

    private static final String URL_IMAGEN =
            "https://res.cloudinary.com/test/image/upload/v1/caa-sp/pacientes/abc/pictogramas/foto.png";

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
    private PictogramaCustomRepository pictogramaCustomRepository;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    private MockMvc mockMvc;
    private String tokenValido;
    private UUID pacienteId;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        pacienteId = UUID.randomUUID();

        Usuario terapeuta = Usuario.builder()
                .id(UUID.randomUUID())
                .email("terapeuta@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .nombre("Nico")
                .apellido("Perez")
                .build();

        tokenValido = jwtService.generarToken(terapeuta);
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findById(pacienteId)).willReturn(Optional.of(paciente));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())).willReturn(Optional.of(paciente));
        given(cloudinaryService.subirImagen(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(pacienteId)))
                .willReturn(URL_IMAGEN);
    }

    @Test
    @DisplayName("POST multipart con token válido → 201 y devuelve la URL de Cloudinary en la respuesta")
    void postMultipart_tokenValido_creaYDevuelveUrl() throws Exception {
        MockMultipartFile imagen = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3, 4});

        PictogramaCustom guardado = PictogramaCustom.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .etiqueta("Mi foto")
                .imagenUrl(URL_IMAGEN)
                .build();
        given(pictogramaCustomRepository.save(org.mockito.ArgumentMatchers.any(PictogramaCustom.class)))
                .willReturn(guardado);

        mockMvc.perform(multipart("/api/pacientes/{pacienteId}/pictogramas-custom", pacienteId)
                        .file(imagen)
                        .param("etiqueta", "Mi foto")
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagenUrl").value(URL_IMAGEN))
                .andExpect(jsonPath("$.etiqueta").value("Mi foto"));
    }

    @Test
    @DisplayName("POST multipart SIN token → 401 (protegido por seguridad)")
    void postMultipart_sinToken_401() throws Exception {
        MockMultipartFile imagen = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/pacientes/{pacienteId}/pictogramas-custom", pacienteId)
                        .file(imagen)
                        .param("etiqueta", "Mi foto"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST multipart sin etiqueta → 400 (validación @NotBlank)")
    void postMultipart_sinEtiqueta_400() throws Exception {
        MockMultipartFile imagen = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/pacientes/{pacienteId}/pictogramas-custom", pacienteId)
                        .file(imagen)
                        .param("etiqueta", "")
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isBadRequest());
    }
}