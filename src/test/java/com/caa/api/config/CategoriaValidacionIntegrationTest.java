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
@DisplayName("CategoriaDTO — validación de colorHex ante deserialización real")
class CategoriaValidacionIntegrationTest {

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

    @Test
    @DisplayName("colorHex con formato inválido → devuelve 400 (validación), NO 500 (deserialización)")
    void colorHexInvalido_devuelve400_no500() throws Exception {
        String body = """
                {
                  "nombre": "Comida",
                  "colorHex": "rojo"
                }
                """;

        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias",
                        pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("colorHex correcto (#RRGGBB) → NO devuelve 400 por validación, llega al service y da 404 (ownership)")
    void colorHexValido_noDevuelve400PorValidacion() throws Exception {
        String body = """
                {
                  "nombre": "Comida",
                  "colorHex": "#E0E0E0"
                }
                """;

        // El body válido pasa la validación de bean; como los owners no están en DB
        // (mocks de repositorios no configurados), el service lanza RecursoNoEncontradoException → 404.
        // La clave: el error NO es de colorHex. Distingue "validación de color mala" (400)
        // de "recurso no encontrado" (404).
        mockMvc.perform(post("/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias",
                        pacienteId, cartillaId)
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
