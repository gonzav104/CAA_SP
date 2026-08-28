package com.caa.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CaaSpApplication {

    public static void main(String[] args) {
        // Carga .env como system properties ANTES de que Spring Boot arranque.
        // Si ya existe una env var en el sistema, la del .env NO la sobreescribe.
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .systemProperties()
                .load();

        SpringApplication.run(CaaSpApplication.class, args);
    }

}
