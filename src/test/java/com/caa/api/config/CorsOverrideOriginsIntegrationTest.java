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
 * despliegue-backend, escenario "Override replaces default".
 *
 * RED contra SecurityConfig hardcodeado: hoy no existe forma de reemplazar la
 * lista de origenes, por lo que este override no tendría efecto y
 * localhost:5173 seguiría siendo aceptado. Debe fallar hasta que
 * SecurityConfig lea {@code app.cors.allowed-origins}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caacorsoverride;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        "app.rate-limit.enabled=false",
        "app.cors.allowed-origins=https://caa.example.com"
})
@DisplayName("CORS — override con CORS_ALLOWED_ORIGINS=https://caa.example.com")
class CorsOverrideOriginsIntegrationTest {

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
    @DisplayName("Origin http://localhost:5173 es rechazado (ya no está en la lista)")
    void localhost5173_esRechazado() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Origin https://caa.example.com es aceptado (nuevo valor configurado)")
    void caaExampleCom_esAceptado() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Origin", "https://caa.example.com")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://caa.example.com"));
    }
}
