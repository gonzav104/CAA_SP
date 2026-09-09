package com.caa.api.config;

import com.caa.api.models.PictogramaGlobal;
import com.caa.api.repositories.PictogramaGlobalRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSeeder — seed de pictogramas globales ARASAAC")
class DataSeederTest {

    private static final String URL_AGUA =
            "https://static.arasaac.org/pictograms/32464/32464_300.png";

    @Mock private PictogramaGlobalRepository pictogramaGlobalRepository;

    @InjectMocks private DataSeeder dataSeeder;

    @Test
    @DisplayName("Si ya hay pictogramas globales → no inserta nada (idempotente)")
    void libreriaPoblada_noInsertaNada() {
        given(pictogramaGlobalRepository.count()).willReturn(5L);

        dataSeeder.run();

        verify(pictogramaGlobalRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("Si la librería está vacía → inserta el set inicial con etiqueta y URL ARASAAC")
    void libreriaVacia_insertaSetInicial() {
        given(pictogramaGlobalRepository.count()).willReturn(0L);

        dataSeeder.run();

        ArgumentCaptor<List<PictogramaGlobal>> captor = ArgumentCaptor.forClass(List.class);
        verify(pictogramaGlobalRepository).saveAll(captor.capture());

        List<PictogramaGlobal> insertados = captor.getValue();
        assertThat(insertados).hasSize(24);
        assertThat(insertados).allSatisfy(p -> {
            assertThat(p.getEtiqueta()).isNotBlank();
            assertThat(p.getImagenUrl()).startsWith("https://static.arasaac.org/pictograms/")
                    .endsWith("_300.png");
            assertThat(p.getArasaacId()).isPositive();
        });

        PictogramaGlobal agua = insertados.stream()
                .filter(p -> p.getEtiqueta().equals("agua"))
                .findFirst()
                .orElseThrow();
        assertThat(agua.getImagenUrl()).isEqualTo(URL_AGUA);

        // Palabras núcleo CAA presentes, incluidas acentuadas
        assertThat(insertados).extracting(PictogramaGlobal::getEtiqueta)
                .contains("agua", "comer", "dormir", "mamá", "papá", "sí", "no", "por favor", "gracias");
    }
}