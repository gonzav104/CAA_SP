package com.caa.api.config;

import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolUsuario;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:caatestpf;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000"
})
@DisplayName("PacienteFamiliar — Persistencia del enum contra H2")
class PacienteFamiliarPersistenceTest {

    @Autowired
    private PacienteFamiliarRepository pacienteFamiliarRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Persiste un PacienteFamiliar y el enum PermisoColaborador se guarda y lee bien")
    void persisteYLeeEnumCorrectamente() {
        // Armado de entidades hijas: terapeuta, paciente y familiar
        Usuario terapeuta = usuarioRepository.save(Usuario.builder()
                .email("terapeuta@ejemplo.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Terapeuta")
                .rol(RolUsuario.TERAPEUTA)
                .build());

        Paciente paciente = pacienteRepository.save(Paciente.builder()
                .terapeuta(terapeuta)
                .nombre("Nico")
                .apellido("Perez")
                .fechaNacimiento(LocalDate.of(2015, 5, 10))
                .build());

        Usuario familiar = usuarioRepository.save(Usuario.builder()
                .email("familiar@ejemplo.com")
                .passwordHash(passwordEncoder.encode("segura123"))
                .nombre("Mama de Nico")
                .rol(RolUsuario.FAMILIAR)
                .build());

        // Persistir el vínculo paciente-familiar con enum
        PacienteFamiliarId id = new PacienteFamiliarId(paciente.getId(), familiar.getId());
        PacienteFamiliar vinculo = PacienteFamiliar.builder()
                .id(id)
                .paciente(paciente)
                .usuario(familiar)
                .permiso(PermisoColaborador.EDICION_LIMITADA)
                .build();

        PacienteFamiliar guardado = pacienteFamiliarRepository.save(vinculo);

        // Leer de nuevo y verificar que el enum se guardó/leyó correctamente
        Optional<PacienteFamiliar> leido = pacienteFamiliarRepository.findById(guardado.getId());
        assertThat(leido).isPresent();
        assertThat(leido.get().getPermiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);
        assertThat(leido.get().getPaciente().getId()).isEqualTo(paciente.getId());
        assertThat(leido.get().getUsuario().getId()).isEqualTo(familiar.getId());

        // Verificar el query por usuario familiar
        List<PacienteFamiliar> porFamiliar = pacienteFamiliarRepository.findByUsuario_Id(familiar.getId());
        assertThat(porFamiliar).hasSize(1);
        assertThat(porFamiliar.get(0).getPermiso()).isEqualTo(PermisoColaborador.EDICION_LIMITADA);

        // Limpiar para no contaminar otros tests (H2 es por-clase)
        pacienteFamiliarRepository.deleteAll();
    }
}
