package com.caa.api.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Paciente;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        "spring.datasource.url=jdbc:h2:mem:caatestpd;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("PacienteDetalle — GET /api/pacientes/{id}")
class PacienteDetalleIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PacienteRepository pacienteRepository;

    private MockMvc mockMvc;
    private String tokenValido;
    private UUID pacienteId;
    private UUID terapeutaId;
    private Usuario terapeuta;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        pacienteId = UUID.randomUUID();
        terapeutaId = UUID.randomUUID();

        terapeuta = Usuario.builder()
                .id(terapeutaId)
                .email("terapeuta@test.com")
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();

        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2020, 5, 10))
                .creadoEn(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();

        tokenValido = jwtService.generarToken(terapeuta);
        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeutaId))
                .willReturn(Optional.of(paciente));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} autenticado como terapeuta propietario → 200 con datos")
    void getPaciente_tokenValido_devuelve200() throws Exception {
        mockMvc.perform(get("/api/pacientes/{id}", pacienteId)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pacienteId.toString()))
                .andExpect(jsonPath("$.nombre").value("Nico"))
                .andExpect(jsonPath("$.apellido").value("Perez"))
                .andExpect(jsonPath("$.fechaNacimiento").value("2020-05-10"));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} autenticado pero paciente inexistente → 404")
    void getPaciente_pacienteInexistente_devuelve404() throws Exception {
        UUID pacienteInvalido = UUID.randomUUID();
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteInvalido, terapeutaId))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/pacientes/{id}", pacienteInvalido)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} SIN token → 401 (no es público)")
    void getPaciente_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/pacientes/{id}", pacienteId))
                .andExpect(status().isUnauthorized());
    }
}