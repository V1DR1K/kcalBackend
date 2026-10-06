package com.scalegrams.nutrition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.PageResponse;
import com.scalegrams.common.PaginationProperties;
import com.scalegrams.common.SearchTextNormalizer;
import com.scalegrams.nutrition.NutritionDtos.FoodSummaryResponse;

@Service
public class FoodCatalogSearchService {
    private final FoodRepository foods;
    private final FoodSemanticSearchService semanticFoods;
    private final FoodNutrientMapper nutrientMapper;
    private final PaginationProperties pagination;
    private final boolean postgres;

    public FoodCatalogSearchService(FoodRepository foods, FoodSemanticSearchService semanticFoods,
            FoodNutrientMapper nutrientMapper, PaginationProperties pagination,
            @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}") String driver) {
        this.foods = foods;
        this.semanticFoods = semanticFoods;
        this.nutrientMapper = nutrientMapper;
        this.pagination = pagination;
        this.postgres = driver.toLowerCase().contains("postgres");
    }

    @Transactional(readOnly = true)
    public PageResponse<FoodSummaryResponse> search(String query, FoodCategory category, int page, int size) {
        int normalizedPage = pagination.normalizePage(page);
        int normalizedSize = pagination.normalizeSize(size);
        Pageable pageable = pagination.pageRequest(page, size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        query = SearchTextNormalizer.normalize(query);
        boolean hasQuery = !query.isBlank();
        if (hasQuery) pageable = pagination.pageRequest(page, size, Sort.unsorted());
        if (hasQuery) {
            if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
            if (query.length() < 2) return PageResponse.from(new PageImpl<>(List.of(), pageable, 0));
        }

        Page<Food> result;
        if (hasQuery && category != null) {
            result = postgres ? searchHybrid(query, category, normalizedPage, normalizedSize)
                    : foods.search(query, category, ModerationStatus.APPROVED, pageable);
        } else if (hasQuery) {
            result = postgres ? searchHybrid(query, null, normalizedPage, normalizedSize)
                    : foods.search(query, ModerationStatus.APPROVED, pageable);
        } else if (category != null) {
            result = foods.findByModerationStatusAndCategoryAndDeletedAtIsNull(ModerationStatus.APPROVED, category,
                    pageable);
        } else {
            result = foods.findByModerationStatusAndDeletedAtIsNull(ModerationStatus.APPROVED, pageable);
        }
        return PageResponse.from(result.map(nutrientMapper::toSummaryResponse));
    }

    private Page<Food> searchHybrid(String query, FoodCategory category, int page, int size) {
        int offset = page * size;
        if (offset >= 500) return semanticPage(query, category, page, size);

        int poolSize = Math.min(500, Math.max(size, offset + size));
        PageRequest poolPage = PageRequest.of(0, poolSize);
        List<FoodSemanticSearchService.FoodMatch> semanticMatches = semanticFoods.search(query, category, null, poolSize);
        if (semanticMatches.isEmpty()) return semanticPage(query, category, page, size);

        Page<Food> lexical = category == null
                ? foods.semanticSearch(query, ModerationStatus.APPROVED.name(), poolPage)
                : foods.semanticSearch(query, category.name(), ModerationStatus.APPROVED.name(), poolPage);
        Map<Long, RankedFood> ranked = new LinkedHashMap<>();
        for (int index = 0; index < lexical.getContent().size(); index++) {
            Food food = lexical.getContent().get(index);
            ranked.computeIfAbsent(food.getId(), ignored -> new RankedFood(food)).addLexicalRank(index);
        }
        for (int index = 0; index < semanticMatches.size(); index++) {
            FoodSemanticSearchService.FoodMatch match = semanticMatches.get(index);
            ranked.computeIfAbsent(match.food().getId(), ignored -> new RankedFood(match.food()))
                    .addSemanticRank(index, match.similarity());
        }
        List<Food> ordered = ranked.values().stream().sorted(RankedFood.ORDER).map(RankedFood::food).toList();
        int from = Math.min(offset, ordered.size());
        int to = Math.min(from + size, ordered.size());
        long total = Math.max(lexical.getTotalElements(), ordered.size());
        return new PageImpl<>(ordered.subList(from, to), PageRequest.of(page, size), total);
    }

    private Page<Food> semanticPage(String query, FoodCategory category, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return category == null
                ? foods.semanticSearch(query, ModerationStatus.APPROVED.name(), pageable)
                : foods.semanticSearch(query, category.name(), ModerationStatus.APPROVED.name(), pageable);
    }

    private static final class RankedFood {
        private static final java.util.Comparator<RankedFood> ORDER = java.util.Comparator
                .comparingDouble(RankedFood::score).reversed()
                .thenComparing(java.util.Comparator.comparingDouble(RankedFood::similarity).reversed())
                .thenComparing(rank -> rank.food.getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(rank -> rank.food.getId());
        private final Food food;
        private double score;
        private double similarity;

        private RankedFood(Food food) { this.food = food; }
        private void addLexicalRank(int rank) { score += 1.0 / (rank + 1.0); }
        private void addSemanticRank(int rank, double similarity) {
            this.similarity = similarity;
            score += 0.5 * similarity / (rank + 1.0);
        }
        private double score() { return score; }
        private double similarity() { return similarity; }
        private Food food() { return food; }
    }
}
