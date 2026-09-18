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
 * Triangulación adicional para el Requirement "Parameterized CORS With
 * Preserved Default": el override debe tolerar espacios alrededor de la coma
 * (split(",") + trim()), no solo el caso feliz sin espacios cubierto por
 * {@link CorsOverrideOriginsIntegrationTest}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caacorstrim;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key",
        "app.rate-limit.enabled=false",
        "app.cors.allowed-origins= https://uno.example.com , https://dos.example.com "
})
@DisplayName("CORS — override con espacios alrededor de la coma")
class CorsTrimmedOriginsIntegrationTest {

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
    @DisplayName("Segundo origen con espacio a la izquierda es aceptado tras el trim()")
    void segundoOrigenConEspacio_esAceptado() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Origin", "https://dos.example.com")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://dos.example.com"));
    }
}
