package com.vitalitypeak.kcal.externalfood;

import java.text.Normalizer;
import java.util.Collection;

import com.vitalitypeak.kcal.catalog.FoodCategory;

final class FoodCategoryClassifier {
    private FoodCategoryClassifier() {}

    static FoodCategory classify(String name, String brand, Collection<String> tags) {
        String value = normalize((name == null ? "" : name) + " " + (brand == null ? "" : brand) + " "
                + (tags == null ? "" : String.join(" ", tags)));
        if (has(value, "beverage", "drink", "soda", "cola", "juice", "water", "bebida", "gaseosa", "jugo")) return FoodCategory.BEVERAGE;
        if (has(value, "alfajor", "candy", "candies", "chocolate", "confection", "sweet", "caramelo", "golosina", "gomita", "turron", "dulce de leche")) return FoodCategory.SWEET;
        if (has(value, "snack", "chips", "crisps", "nacho", "chizito", "palito salado", "popcorn", "pochoclo")) return FoodCategory.SNACK;
        if (has(value, "bread", "bakery", "pastr", "pan ", "panificado", "factura", "medialuna", "tortilla", "bizcocho")) return FoodCategory.BAKERY;
        if (has(value, "legume", "lentil", "chickpea", "bean", "pea", "soy", "lenteja", "garbanzo", "poroto", "arveja", "soja")) return FoodCategory.LEGUME;
        if (has(value, "meat", "beef", "pork", "chicken", "turkey", "carne", "pollo", "cerdo", "vacuno", "hamburg")) return FoodCategory.MEAT;
        if (has(value, "milk", "cheese", "yogurt", "yoghurt", "dairy", "leche", "queso", "yogur", "lacteo")) return FoodCategory.DAIRY;
        if (has(value, "fruit", "fruta")) return FoodCategory.FRUIT;
        if (has(value, "vegetable", "verdura", "hortaliza")) return FoodCategory.VEGETABLE;
        if (has(value, "cereal", "pasta", "rice", "arroz", "fideo", "avena", "galletita", "cookie", "biscuit")) return FoodCategory.CEREAL;
        if (has(value, "oil", "butter", "mayonnaise", "aceite", "manteca", "mayonesa", "nuts", "mani", "almendra", "nuez")) return FoodCategory.FAT;
        if (has(value, "fish", "egg", "protein", "pescado", "huevo", "proteina")) return FoodCategory.PROTEIN;
        return FoodCategory.OTHER;
    }

    private static boolean has(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}
