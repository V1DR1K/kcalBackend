package com.scalegrams.common;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import com.scalegrams.catalog.Food;
public class ArchivedFoodAcknowledgementException extends RuntimeException {
    private final Map<String, String> fields;
    public ArchivedFoodAcknowledgementException(List<Food> foods) {
        super("Hay alimentos archivados. Revisá el aviso antes de reutilizarlos.");
        fields = Map.of("archivedFoodIds", foods.stream().map(food -> food.getId().toString()).collect(Collectors.joining(",")),
            "archivedFoodNames", foods.stream().map(Food::getName).collect(Collectors.joining(", ")));
    }
    public Map<String, String> getFields() { return fields; }
}
