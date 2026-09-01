package com.caa.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "CAA_SP API",
                description = "API de Comunicación Aumentativa y Alternativa para familias de San Pedro",
                version = "1.0.0"
        )
)
public class OpenApiConfig {
}
