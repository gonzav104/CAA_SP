package com.caa.api.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.models.Paciente;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Sesion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.SesionRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import java.time.LocalDateTime;
import java.util.List;
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
 * Sesiones = recurso clínico del TERAPEUTA. Ni siquiera un familiar vinculado las ve
 * (a diferencia de cartillas, donde el familiar SÍ lee). Lock del escenario 8 de la spec.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caasesiones;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret"
})
@DisplayName("Sesiones — recurso clínico terapeuta-only")
class SesionesIntegrationTest {

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
    private SesionRepository sesionRepository;

    private MockMvc mockMvc;
    private UUID pacienteId;
    private Usuario terapeuta;
    private Usuario familiar;
    private Paciente paciente;
    private String tokenTerapeuta;
    private String tokenFamiliar;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        pacienteId = UUID.randomUUID();
        terapeuta = Usuario.builder()
                .id(UUID.randomUUID())
                .email("terapeuta@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build();
        familiar = Usuario.builder()
                .id(UUID.randomUUID())
                .email("familiar@test.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Familiar")
                .rol(RolUsuario.FAMILIAR)
                .build();
        paciente = Paciente.builder()
                .id(pacienteId)
                .terapeuta(terapeuta)
                .nombre("Nico")
                .apellido("Perez")
                .build();

        tokenTerapeuta = jwtService.generarToken(terapeuta);
        tokenFamiliar = jwtService.generarToken(familiar);

        given(usuarioRepository.findByEmail("terapeuta@test.com")).willReturn(Optional.of(terapeuta));
        given(usuarioRepository.findByEmail("familiar@test.com")).willReturn(Optional.of(familiar));
    }

    @Test
    @DisplayName("GET sesiones: terapeuta propietario → 200 con lista")
    void getSesiones_terapeutaPropietario_200() throws Exception {
        Sesion s1 = Sesion.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .fechaHora(LocalDateTime.of(2026, 9, 1, 10, 0))
                .objetivosTrabajados("Objetivo A")
                .observaciones("Obs")
                .estrategiasYProximosPasos("Sigue")
                .build();

        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId()))
                .willReturn(Optional.of(paciente));
        given(sesionRepository.findByPacienteId(pacienteId)).willReturn(List.of(s1));

        mockMvc.perform(get("/api/pacientes/{pacienteId}/sesiones", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].objetivosTrabajados").value("Objetivo A"))
                .andExpect(jsonPath("$[0].pacienteId").value(pacienteId.toString()));
    }

    @Test
    @DisplayName("GET sesiones: familiar (incluso vinculado) → 404, las sesiones NO se comparten")
    void getSesiones_familiar_404() throws Exception {
        // El service ahora valida contra findByIdAndTerapeutaId: el familiar nunca es
        // terapeuta, así que el look-up queda vacío → 404 genérico. El vínculo vía
        // PacienteFamiliar es IRRELEVANTE para sesiones (a diferencia de cartillas).
        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, familiar.getId()))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/pacientes/{pacienteId}/sesiones", pacienteId)
                        .header("Authorization", "Bearer " + tokenFamiliar))
                .andExpect(status().isNotFound());

        verify(sesionRepository, never()).findByPacienteId(any());
    }

    @Test
    @DisplayName("POST sesión: terapeuta propietario → 201")
    void postSesion_terapeutaPropietario_201() throws Exception {
        String body = """
                {
                  "fechaHora": "2026-09-02T09:00:00",
                  "disposicion": "sentado",
                  "objetivosTrabajados": "Trabajar vocabulario",
                  "observaciones": "Buena sesión",
                  "estrategiasYProximosPasos": "Continuar con verbos"
                }
                """;

        Sesion guardada = Sesion.builder()
                .id(UUID.randomUUID())
                .paciente(paciente)
                .fechaHora(LocalDateTime.of(2026, 9, 2, 9, 0))
                .disposicion("sentado")
                .objetivosTrabajados("Trabajar vocabulario")
                .observaciones("Buena sesión")
                .estrategiasYProximosPasos("Continuar con verbos")
                .build();

        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId()))
                .willReturn(Optional.of(paciente));
        given(sesionRepository.save(any(Sesion.class))).willReturn(guardada);

        mockMvc.perform(post("/api/pacientes/{pacienteId}/sesiones", pacienteId)
                        .header("Authorization", "Bearer " + tokenTerapeuta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objetivosTrabajados").value("Trabajar vocabulario"))
                .andExpect(jsonPath("$.pacienteId").value(pacienteId.toString()));
    }

    @Test
    @DisplayName("POST sesión: familiar → 404 (terapeuta-only)")
    void postSesion_familiar_404() throws Exception {
        String body = """
                {
                  "fechaHora": "2026-09-02T09:00:00",
                  "objetivosTrabajados": "Objetivo"
                }
                """;

        given(pacienteRepository.findByIdAndTerapeutaId(pacienteId, familiar.getId()))
                .willReturn(Optional.empty());

        mockMvc.perform(post("/api/pacientes/{pacienteId}/sesiones", pacienteId)
                        .header("Authorization", "Bearer " + tokenFamiliar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        verify(sesionRepository, never()).save(any());
    }

    @Test
    @DisplayName("GET sesiones: SIN token → 401")
    void getSesiones_sinToken_401() throws Exception {
        mockMvc.perform(get("/api/pacientes/{pacienteId}/sesiones", pacienteId))
                .andExpect(status().isUnauthorized());
    }
}