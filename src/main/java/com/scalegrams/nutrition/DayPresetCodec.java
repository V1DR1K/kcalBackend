package com.scalegrams.nutrition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.nutrition.NutritionDtos.CreateDayPresetRequest;
import com.scalegrams.nutrition.NutritionDtos.DayPresetItemRequest;
import com.scalegrams.nutrition.NutritionDtos.DayPresetResponse;
import com.scalegrams.nutrition.NutritionDtos.UpdateDayPresetRequest;

@Component
public class DayPresetCodec {
    private final ObjectMapper objectMapper;

    public DayPresetCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DayPresetItemRequest> validate(List<DayPresetItemRequest> items) {
        if (items == null || items.isEmpty()) throw new BadRequestException("El día no tiene alimentos para guardar.");
        for (DayPresetItemRequest item : items) {
            if (item.itemType() == MealItemType.AI_ESTIMATE) {
                if (item.itemId() != null) throw new BadRequestException("La estimación no puede tener alimento asociado.");
            } else if (item.itemId() == null || item.itemId() <= 0) {
                throw new BadRequestException("El preset contiene un alimento inválido.");
            }
        }
        return items;
    }

    public String write(List<DayPresetItemRequest> items) {
        try { return objectMapper.writeValueAsString(validate(items)); }
        catch (JsonProcessingException error) { throw new BadRequestException("No se pudo guardar el contenido del preset."); }
    }

    public List<DayPresetItemRequest> read(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<DayPresetItemRequest>>() {}); }
        catch (JsonProcessingException error) { throw new BadRequestException("El contenido del preset no es válido."); }
    }

    public DayPresetResponse toResponse(DayPreset preset) {
        List<DayPresetItemRequest> items = read(preset.getItemsJson());
        Map<String, Integer> mealCounts = items.stream().collect(Collectors.groupingBy(item -> item.mealType().name(),
                LinkedHashMap::new, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        return new DayPresetResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getCreatedAt(),
                preset.getUpdatedAt(), items, items.size(), mealCounts);
    }
}
