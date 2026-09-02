package com.caa.api.dtos;

import com.caa.api.models.RolUsuario;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UsuarioRegistroDTO — Validación de contraseña fuerte")
class UsuarioRegistroDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    private Set<ConstraintViolation<UsuarioRegistroDTO>> validar(String password) {
        UsuarioRegistroDTO dto = new UsuarioRegistroDTO(
                "test@ejemplo.com",
                password,
                "Test User",
                RolUsuario.TERAPEUTA);
        return validator.validate(dto);
    }

    @Test
    @DisplayName("Contraseña débil 'abc123' → produce violación de contraseña")
    void passwordDebil_produceViolacion() {
        Set<ConstraintViolation<UsuarioRegistroDTO>> violaciones = validar("abc123");

        assertThat(violaciones)
                .anyMatch(v -> "password".equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("Contraseña vacía → produce violación")
    void passwordVacia_produceViolacion() {
        Set<ConstraintViolation<UsuarioRegistroDTO>> violaciones = validar("");

        assertThat(violaciones)
                .anyMatch(v -> "password".equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("Contraseña fuerte 'Contraseña1!' → sin violaciones de contraseña")
    void passwordFuerte_sinViolacionDePassword() {
        Set<ConstraintViolation<UsuarioRegistroDTO>> violaciones = validar("Contraseña1!");

        assertThat(violaciones)
                .noneMatch(v -> "password".equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("Consistencia con frontend: 'Contraseña1' (ñ sin símbolo real) → violación de contraseña")
    void passwordConEnieSinSimbolo_produceViolacion() {
        // La ñ es una letra (Unicode \p{L}), NO un símbolo.
        // Debe coincidir con el frontend (CAA_Front/src/schemas/auth.ts) que usa [^\p{L}\p{N}].
        Set<ConstraintViolation<UsuarioRegistroDTO>> violaciones = validar("Contraseña1");

        assertThat(violaciones)
                .anySatisfy(v -> {
                    assertThat("password").isEqualTo(v.getPropertyPath().toString());
                    assertThat(v.getMessage()).isEqualTo(
                            "La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo");
                });
    }
}
