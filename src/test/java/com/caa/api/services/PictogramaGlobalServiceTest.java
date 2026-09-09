package com.caa.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.caa.api.dtos.MaterializarPictogramaDTO;
import com.caa.api.dtos.MaterializarResultadoDTO;
import com.caa.api.models.PictogramaGlobal;
import com.caa.api.repositories.PictogramaGlobalRepository;
import com.caa.api.services.impl.PictogramaGlobalServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Materialización de pictogramas ARASAAC en la librería global: alta con URL
 * derivada, dedupe idempotente por {@code arasaacId} y recuperación ante la
 * carrera de concurrencia (unique constraint + catch).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PictogramaGlobalService — materializar con dedupe")
class PictogramaGlobalServiceTest {

    @Mock PictogramaGlobalRepository pictogramaGlobalRepository;

    @InjectMocks PictogramaGlobalServiceImpl pictogramaGlobalService;

    @Test
    @DisplayName("Materializar arasaacId nuevo → inserta con URL derivada y recienCreado=true")
    void materializar_nuevo_creaConUrlDerivada() {
        long arasaacId = 12345L;
        String imagenEsperada = "https://static.arasaac.org/pictograms/12345/12345_300.png";

        PictogramaGlobal guardado = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("pelota")
                .imagenUrl(imagenEsperada)
                .arasaacId(arasaacId)
                .build();

        given(pictogramaGlobalRepository.findByArasaacId(arasaacId)).willReturn(Optional.empty());
        given(pictogramaGlobalRepository.save(any(PictogramaGlobal.class))).willReturn(guardado);

        MaterializarResultadoDTO resultado =
                pictogramaGlobalService.materializar(new MaterializarPictogramaDTO(arasaacId, "pelota"));

        assertThat(resultado.recienCreado()).isTrue();
        assertThat(resultado.pictograma().id()).isEqualTo(guardado.getId());
        assertThat(resultado.pictograma().imagenUrl()).isEqualTo(imagenEsperada);
        assertThat(resultado.pictograma().arasaacId()).isEqualTo(arasaacId);
    }

    @Test
    @DisplayName("Materializar arasaacId existente → devuelve el existente SIN insertar (recienCreado=false)")
    void materializar_existente_devuelveExistenteSinInsertar() {
        long arasaacId = 99999L;
        PictogramaGlobal existente = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("agua")
                .imagenUrl("https://static.arasaac.org/pictograms/99999/99999_300.png")
                .arasaacId(arasaacId)
                .build();

        given(pictogramaGlobalRepository.findByArasaacId(arasaacId)).willReturn(Optional.of(existente));

        MaterializarResultadoDTO resultado =
                pictogramaGlobalService.materializar(new MaterializarPictogramaDTO(arasaacId, "agua"));

        assertThat(resultado.recienCreado()).isFalse();
        assertThat(resultado.pictograma().id()).isEqualTo(existente.getId());
        verify(pictogramaGlobalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Carrera: save lanza DataIntegrityViolationException → recupera el existente (recienCreado=false)")
    void materializar_carreraConcurrente_recuperaExistente() {
        long arasaacId = 77777L;
        PictogramaGlobal existente = PictogramaGlobal.builder()
                .id(UUID.randomUUID())
                .etiqueta("casa")
                .imagenUrl("https://static.arasaac.org/pictograms/77777/77777_300.png")
                .arasaacId(arasaacId)
                .build();

        // Primer chequeo: no existe (otro request aún no insertó)
        given(pictogramaGlobalRepository.findByArasaacId(arasaacId))
                .willReturn(Optional.empty(), Optional.of(existente));
        // El insert pisa el unique constraint del request concurrente
        given(pictogramaGlobalRepository.save(any(PictogramaGlobal.class)))
                .willThrow(new DataIntegrityViolationException("uq_pictogramas_globales_arasaac_id"));

        MaterializarResultadoDTO resultado =
                pictogramaGlobalService.materializar(new MaterializarPictogramaDTO(arasaacId, "casa"));

        assertThat(resultado.recienCreado()).isFalse();
        assertThat(resultado.pictograma().id()).isEqualTo(existente.getId());
    }
}