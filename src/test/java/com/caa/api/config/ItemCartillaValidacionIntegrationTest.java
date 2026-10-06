package com.caa.api.config;

import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("ItemCartilla DTO — validación de textoVisible ante deserialización real")
class ItemCartillaValidacionIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private MockMvc mockMvc;

    private Usuario usuarioTest;
    private String tokenValido;
    private UUID pacienteId = UUID.randomUUID();
    private UUID cartillaId = UUID.randomUUID();
    private UUID categoriaId = UUID.randomUUID();
    private UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        usuarioTest = Usuario.builder()
                .id(UUID.randomUUID())
                .email("integracion@ejemplo.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Integration Test")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        tokenValido = jwtService.generarToken(usuarioTest);
        given(usuarioRepository.findByEmail("integracion@ejemplo.com"))
                .willReturn(Optional.of(usuarioTest));
    }

    private static final String BASE = "/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias/{categoriaId}/items";

    private String json(String textoVisible) {
        String campo = textoVisible == null ? "" : ", \"textoVisible\": \"" + textoVisible + "\"";
        return "{\"textoHablado\": \"Quiero ir al baño\", \"recursoGlobalId\": \"" + UUID.randomUUID() + "\"" + campo + "}";
    }

    private int enviarPost(String textoVisible) throws Exception {
        return mockMvc.perform(post(BASE, pacienteId, cartillaId, categoriaId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(textoVisible)))
                .andReturn().getResponse().getStatus();
    }

    private int enviarPut(String textoVisible) throws Exception {
        return mockMvc.perform(put(BASE + "/{itemId}", pacienteId, cartillaId, categoriaId, itemId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(textoVisible)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("POST: textoVisible vacío o solo espacios → 400")
    void post_textoVisibleBlank_devuelve400() throws Exception {
        org.assertj.core.api.Assertions.assertThat(enviarPost("")).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(enviarPost("   ")).isEqualTo(400);
    }

    @Test
    @DisplayName("POST: textoVisible de más de 30 caracteres → 400")
    void post_textoVisibleLargo_devuelve400() throws Exception {
        org.assertj.core.api.Assertions.assertThat(enviarPost("A".repeat(31))).isEqualTo(400);
    }

    @Test
    @DisplayName("POST: textoVisible de 30 caracteres o ausente → pasa la validación (404 por ownership, no 400)")
    void post_textoVisibleValido_noDevuelve400() throws Exception {
        org.assertj.core.api.Assertions.assertThat(enviarPost("A".repeat(30))).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(enviarPost(null)).isEqualTo(404);
    }

    @Test
    @DisplayName("PUT: textoVisible vacío o solo espacios → 400")
    void put_textoVisibleBlank_devuelve400() throws Exception {
        org.assertj.core.api.Assertions.assertThat(enviarPut("")).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(enviarPut("   ")).isEqualTo(400);
    }

    @Test
    @DisplayName("PUT: textoVisible de más de 30 caracteres → 400")
    void put_textoVisibleLargo_devuelve400() throws Exception {
        org.assertj.core.api.Assertions.assertThat(enviarPut("A".repeat(31))).isEqualTo(400);
    }

    @Test
    @DisplayName("PUT: textoVisible de 30 caracteres o ausente → pasa la validación (404 por ownership, no 400)")
    void put_textoVisibleValido_noDevuelve400() throws Exception {
        org.assertj.core.api.Assertions.assertThat(enviarPut("A".repeat(30))).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(enviarPut(null)).isEqualTo(404);
    }

    @Test
    @DisplayName("textoHablado en blanco sigue devolviendo 400 (validación existente intacta)")
    void textoHabladoBlank_sigueDevolviendo400() throws Exception {
        mockMvc.perform(post(BASE, pacienteId, cartillaId, categoriaId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textoHablado\": \"  \", \"recursoGlobalId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
