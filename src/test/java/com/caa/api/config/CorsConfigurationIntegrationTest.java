package com.caa.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Cubre Requirement "Parameterized CORS With Preserved Default" del spec de
 * despliegue-backend: sin {@code CORS_ALLOWED_ORIGINS}, el comportamiento debe
 * ser byte-idéntico a la lista hoy hardcodeada en SecurityConfig.
 *
 * Approval test: estas dos aserciones deben pasar ANTES del refactor a
 * {@code app.cors.allowed-origins} (capturan el comportamiento actual) y
 * SEGUIR pasando después (el default de la propiedad es el mismo valor).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caacorsdefault;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        "app.rate-limit.enabled=false"
})
@DisplayName("CORS — default sin CORS_ALLOWED_ORIGINS")
class CorsConfigurationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Origin http://localhost:5173 es aceptado (primer origen del default)")
    void localhost5173_esAceptado() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("Origin http://localhost:5174 es aceptado (segundo origen del default)")
    void localhost5174_esAceptado() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Origin", "http://localhost:5174")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
    }
}
