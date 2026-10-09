package com.caa.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.caa.api.models.Usuario;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/**
 * Cubre el finding de la native review (lens review-reliability, correction sobre f4bc3ce):
 * {@link Usuario#getRol()} fue relajado a {@code @Column(nullable = true)} para soportar el
 * modelo multi-tenant (dual-write, design §6; tarea 1.14 de la Fase 1) y la contraparte de
 * esquema real llega en init.sql, MIGRACIÓN 015a.
 * <p>
 * {@code spring.jpa.hibernate.ddl-auto=create-drop} hace que H2 genere su esquema a partir de
 * las anotaciones JPA de {@link Usuario}, NO a partir de {@code init.sql}. Por lo tanto este
 * test verifica que la anotación de la entidad realmente permite persistir {@code rol = null}
 * (lo que ya cubre el nivel de JPA/Hibernate), pero NO ejercita la restricción real de la
 * columna en PostgreSQL ni la migración 015a en sí — eso solo se puede comprobar contra
 * PostgreSQL real (igual que el resto de las garantías específicas de esta Fase, ver
 * {@link com.caa.api.config.OwnerDeferredTriggerPostgresIntegrationTest}), lo que queda fuera
 * del alcance de esta corrección.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caausuariorepo;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-api-key",
        "cloudinary.api-secret=test-api-secret",
        "resend.api-key=test-resend-api-key"
})
@DisplayName("UsuarioRepository — rol es nullable (H2, a nivel de anotación JPA)")
class UsuarioRepositoryTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Permite persistir un Usuario con rol = null")
    void permitePersistirUsuarioConRolNulo() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("sin-rol-" + UUID.randomUUID() + "@usuario.test")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Sin Rol")
                .rol(null)
                .build());

        Usuario recuperado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(recuperado.getRol()).isNull();
    }
}
