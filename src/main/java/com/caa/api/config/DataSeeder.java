package com.caa.api.config;

import com.caa.api.models.PictogramaGlobal;
import com.caa.api.repositories.PictogramaGlobalRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puebla la tabla {@code pictogramas_globales} con un set inicial de pictogramas
 * ARASAAC (vocabulario núcleo CAA en español) si la librería global está vacía.
 *
 * <p>El seed es idempotente: si ya hay datos no hace nada, así se puede reiniciar
 * la app sin duplicar la librería. No depende de la red en runtime — los IDs
 * ARASAAC reales fueron resueltos contra la API de ARASAAC durante el desarrollo
 * y sus URLs de imagen fueron validadas con HTTP 200.
 *
 * <p>El {@code creadoEn} lo asigna Hibernate automáticamente vía
 * {@code @CreationTimestamp} de la entidad, por eso solo seteamos etiqueta e imagen.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final PictogramaGlobalRepository pictogramaGlobalRepository;

    /** Etiqueta + ID numérico real de ARASAAC. La URL se deriva del ID. */
    private record PictogramaSeed(String etiqueta, int arasaacId) {
        String imagenUrl() {
            return "https://static.arasaac.org/pictograms/" + arasaacId + "/" + arasaacId + "_300.png";
        }
    }

    private static final List<PictogramaSeed> PICTORAMAS_GLOBALES = List.of(
            new PictogramaSeed("agua", 32464),
            new PictogramaSeed("galleta", 8312),
            new PictogramaSeed("leche", 2445),
            new PictogramaSeed("pan", 2494),
            new PictogramaSeed("comer", 6456),
            new PictogramaSeed("beber", 6061),
            new PictogramaSeed("dormir", 6479),
            new PictogramaSeed("baño", 27559),
            new PictogramaSeed("mamá", 2458),
            new PictogramaSeed("papá", 31146),
            new PictogramaSeed("jugar", 23392),
            new PictogramaSeed("más", 3220),
            new PictogramaSeed("no", 5526),
            new PictogramaSeed("sí", 5584),
            new PictogramaSeed("por favor", 8195),
            new PictogramaSeed("gracias", 8129),
            new PictogramaSeed("ayudar", 32648),
            new PictogramaSeed("querer", 11538),
            new PictogramaSeed("dolor", 30620),
            new PictogramaSeed("feliz", 9907),
            new PictogramaSeed("triste", 35545),
            new PictogramaSeed("casa", 6964),
            new PictogramaSeed("colegio", 32446),
            new PictogramaSeed("pelota", 3241)
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (pictogramaGlobalRepository.count() > 0) {
            log.info("Seeder: la librería de pictogramas globales ya tiene datos, se omite el seed");
            return;
        }

        List<PictogramaGlobal> nuevos = PICTORAMAS_GLOBALES.stream()
                .map(p -> PictogramaGlobal.builder()
                        .etiqueta(p.etiqueta())
                        .imagenUrl(p.imagenUrl())
                        .build())
                .toList();

        pictogramaGlobalRepository.saveAll(nuevos);
        log.info("Seeder: {} pictogramas globales insertados", nuevos.size());
    }
}