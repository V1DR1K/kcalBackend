package com.vitalitypeak.kcal.externalfood;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vitalitypeak.kcal.catalog.Food;
import com.vitalitypeak.kcal.catalog.FoodRepository;
import com.vitalitypeak.kcal.catalog.FoodUnit;

@Component
public class OpenFoodFactsBulkImporter implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsBulkImporter.class);
    private final OpenFoodFactsProvider provider;
    private final FoodRepository foods;
    private final boolean enabled;
    private final String brands;
    private final int pagesPerBrand;
    private final int pageSize;
    private final long delayMillis;

    public OpenFoodFactsBulkImporter(OpenFoodFactsProvider provider, FoodRepository foods,
            @Value("${app.catalog-import.enabled:false}") boolean enabled,
            @Value("${app.catalog-import.brands:Coca-Cola,Mogul,Milka,Fantoche,Ciudad del Lago,Arcor,Bagley,Terrabusi,Havanna,Guaymallen,Jorgito,Bon o Bon,Cofler,Aguila,Don Satur,Pepitos,Sonrisas,Tita,Rhodesia,La Serenisima,Pepsi}") String brands,
            @Value("${app.catalog-import.pages-per-brand:3}") int pagesPerBrand,
            @Value("${app.catalog-import.page-size:100}") int pageSize,
            @Value("${app.catalog-import.delay-millis:6500}") long delayMillis) {
        this.provider = provider;
        this.foods = foods;
        this.enabled = enabled;
        this.brands = brands;
        this.pagesPerBrand = Math.max(1, pagesPerBrand);
        this.pageSize = Math.max(1, Math.min(pageSize, 100));
        this.delayMillis = Math.max(6000, delayMillis);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!enabled) return;
        for (String brand : Arrays.stream(brands.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList()) {
            for (int page = 1; page <= pagesPerBrand; page++) {
                var candidates = provider.searchBrandInArgentina(brand, page, pageSize);
                candidates.forEach(candidate -> {
                    try {
                        upsert(candidate);
                    } catch (RuntimeException ex) {
                        log.warn("No se pudo importar el producto {} de {}", candidate.barcode(), brand, ex);
                    }
                });
                log.info("Importación Open Food Facts: marca={}, página={}, productos válidos={}", brand, page, candidates.size());
                if (candidates.size() < pageSize) break;
                Thread.sleep(delayMillis);
            }
            Thread.sleep(delayMillis);
        }
    }

    @Transactional
    void upsert(ExternalFoodCandidate candidate) {
        if (candidate.barcode() == null) return;
        Food food = foods.findByBarcode(candidate.barcode()).orElseGet(Food::new);
        food.setName(candidate.name());
        food.setBrand(candidate.brand());
        food.setBarcode(candidate.barcode());
        food.setCategory(candidate.category());
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setCalories(candidate.calories());
        food.setProteinGrams(candidate.proteinGrams());
        food.setCarbsGrams(candidate.carbsGrams());
        food.setFatGrams(candidate.fatGrams());
        food.setPreparation(candidate.preparation());
        food.setPreparationSource(candidate.preparationSource());
        food.setServingName(candidate.servingName());
        food.setServingWeightGrams(candidate.servingWeightGrams());
        food.setImageUrl(null);
        food.setImageObjectKey(null);
        food.setSource(candidate.source());
        food.setSourceId(candidate.sourceId());
        food.setLastSyncedAt(OffsetDateTime.now());
        food.setTags(candidate.tags() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(candidate.tags()));
        foods.save(food);
    }
}
