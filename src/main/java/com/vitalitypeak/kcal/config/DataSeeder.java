package com.vitalitypeak.kcal.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vitalitypeak.kcal.catalog.Food;
import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.catalog.FoodPreparation;
import com.vitalitypeak.kcal.catalog.FoodRepository;
import com.vitalitypeak.kcal.catalog.FoodUnit;
import com.vitalitypeak.kcal.nutrition.FoodLog;
import com.vitalitypeak.kcal.nutrition.FoodLogRepository;
import com.vitalitypeak.kcal.nutrition.MealType;
import com.vitalitypeak.kcal.nutrition.WaterLog;
import com.vitalitypeak.kcal.nutrition.WaterLogRepository;
import com.vitalitypeak.kcal.profile.NutritionPlan;
import com.vitalitypeak.kcal.profile.NutritionPlanRepository;
import com.vitalitypeak.kcal.user.ActivityLevel;
import com.vitalitypeak.kcal.user.AppUser;
import com.vitalitypeak.kcal.user.FitnessGoal;
import com.vitalitypeak.kcal.user.Gender;
import com.vitalitypeak.kcal.user.Role;
import com.vitalitypeak.kcal.user.UserRepository;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seed(UserRepository users, FoodRepository foods, FoodLogRepository foodLogs,
            WaterLogRepository waterLogs, NutritionPlanRepository nutritionPlans, PasswordEncoder passwordEncoder) {
        return args -> {
            ensureFood(foods, "Pechuga de Pollo", "KazaFitness Premium Select", "7790000000011", FoodCategory.PROTEIN,
                    165, 31, 0, 3.6, FoodPreparation.COOKED, "USDA FDC 171477", Set.of("Alta en Proteina", "Keto Friendly"));
            ensureFood(foods, "Pechuga de Pollo", "KazaFitness Premium Select", "7790000000073", FoodCategory.PROTEIN,
                    120, 22.5, 0, 2.6, FoodPreparation.RAW, "USDA FDC 171077", Set.of("Alta en Proteina", "Keto Friendly"));
            ensureFood(foods, "Arroz Blanco", "Generico", "7790000000028", FoodCategory.CEREAL,
                    130, 2.7, 28, 0.3, FoodPreparation.COOKED, "USDA FDC 168878", Set.of("Carbohidrato"));
            ensureFood(foods, "Palta (Aguacate)", "Fresco", "7790000000035", FoodCategory.FAT,
                    160, 2, 8.5, 14.7, FoodPreparation.RAW, "USDA FDC 171705", Set.of("Grasas Saludables"));
            ensureFood(foods, "Yogur Griego Natural", "KazaFitness Dairy", "7790000000042", FoodCategory.DAIRY,
                    59, 10, 3.6, 0.4, FoodPreparation.AS_SOLD, "USDA FDC 330137", Set.of("Proteina"));
            ensureFood(foods, "Atun en lata", "Mar Azul", "7790000000059", FoodCategory.PROTEIN,
                    116, 26, 0, 1, FoodPreparation.AS_SOLD, "USDA FDC 334194", Set.of("Alta Proteina", "Keto"));
            ensureFood(foods, "Banana", "Fresco", "7790000000066", FoodCategory.FRUIT,
                    89, 1.1, 22.8, 0.3, FoodPreparation.RAW, "USDA FDC 173944", Set.of("Fruta"));
            ensureFood(foods, "Manzana", "Fresco", "7790000000103", FoodCategory.FRUIT,
                    64, 0.3, 14.9, 0.4, FoodPreparation.RAW, "ARGENFOODS 138", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Pera", "Fresco", "7790000000110", FoodCategory.FRUIT,
                    70, 0.7, 15.8, 0.4, FoodPreparation.RAW, "ARGENFOODS 144", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Durazno", "Fresco", "7790000000127", FoodCategory.FRUIT,
                    51, 0.5, 12, 0.1, FoodPreparation.RAW, "ARGENFOODS 129", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Frutilla", "Fresco", "7790000000134", FoodCategory.FRUIT,
                    41, 0.8, 8.1, 0.6, FoodPreparation.RAW, "ARGENFOODS 132", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Sandía", "Fresco", "7790000000141", FoodCategory.FRUIT,
                    31, 0.5, 6.9, 0.2, FoodPreparation.RAW, "ARGENFOODS 147", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Tomate", "Fresco", "7790000000202", FoodCategory.VEGETABLE,
                    20, 1, 4.1, 0, FoodPreparation.RAW, "ARGENFOODS 110", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Lechuga", "Fresco", "7790000000219", FoodCategory.VEGETABLE,
                    15, 1.4, 2.9, 0.2, FoodPreparation.RAW, "USDA FDC 169247", Set.of("Verdura"));
            ensureFood(foods, "Papa", "Fresco", "7790000000226", FoodCategory.VEGETABLE,
                    88, 2.7, 19.3, 0, FoodPreparation.RAW, "ARGENFOODS 87", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Cebolla", "Fresco", "7790000000233", FoodCategory.VEGETABLE,
                    17, 0.8, 3.5, 0, FoodPreparation.RAW, "ARGENFOODS 69", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Zapallo", "Fresco", "7790000000240", FoodCategory.VEGETABLE,
                    27, 0.5, 5.8, 0.2, FoodPreparation.RAW, "ARGENFOODS 430", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Berenjena", "Fresco", "7790000000257", FoodCategory.VEGETABLE,
                    28, 1.1, 5.5, 0.2, FoodPreparation.RAW, "ARGENFOODS 64", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Brócoli", "Fresco", "7790000000264", FoodCategory.VEGETABLE,
                    37, 3.3, 5.5, 0.2, FoodPreparation.RAW, "ARGENFOODS 66", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Espinaca", "Fresco", "7790000000271", FoodCategory.VEGETABLE,
                    24, 3.9, 2, 0, FoodPreparation.RAW, "ARGENFOODS 75", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Lomo vacuno", "Carnicería", "7790000000301", FoodCategory.PROTEIN,
                    116, 20, 0, 4, FoodPreparation.RAW, "ARGENFOODS 233", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Nalga vacuna", "Carnicería", "7790000000318", FoodCategory.PROTEIN,
                    106, 22, 0, 1.7, FoodPreparation.RAW, "ARGENFOODS 510", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Peceto vacuno", "Carnicería", "7790000000325", FoodCategory.PROTEIN,
                    125, 23, 0, 1.9, FoodPreparation.RAW, "ARGENFOODS 514", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Vacío vacuno", "Carnicería", "7790000000332", FoodCategory.PROTEIN,
                    174, 20, 0, 11, FoodPreparation.RAW, "ARGENFOODS 517", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Vacío vacuno", "Carnicería", "7790000000349", FoodCategory.PROTEIN,
                    258, 25.6, 0, 17.3, FoodPreparation.COOKED, "ARGENFOODS 240", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Bife vacuno", "Carnicería", "7790000000356", FoodCategory.PROTEIN,
                    111, 21, 0, 3, FoodPreparation.RAW, "ARGENFOODS 229", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Bife vacuno", "Carnicería", "7790000000363", FoodCategory.PROTEIN,
                    189, 24.6, 0, 10.1, FoodPreparation.COOKED, "ARGENFOODS 227", Set.of("Carne vacuna", "Argentina"));
            setServing(foods, "7790000000028", "Taza cocida", 158);
            setServing(foods, "7790000000035", "Palta mediana", 201);
            setServing(foods, "7790000000066", "Banana mediana", 118);
            setPreparationGroup(foods, "CHICKEN_BREAST", "7790000000011", "7790000000073");
            setPreparationGroup(foods, "BEEF_VACIO", "7790000000332", "7790000000349");
            setPreparationGroup(foods, "BEEF_STEAK", "7790000000356", "7790000000363");

            ensureAdmin(users, passwordEncoder);

            if (!users.existsByEmailIgnoreCase("alex@kazadesarrollos.com")) {
                AppUser user = new AppUser();
                user.setFullName("Alex Rivera");
                user.setEmail("alex@kazadesarrollos.com");
                user.setPasswordHash(passwordEncoder.encode("password123"));
                user.setWeightKg(BigDecimal.valueOf(75));
                user.setHeightCm(BigDecimal.valueOf(180));
                user.setTargetWeightKg(BigDecimal.valueOf(72));
                user.setBirthDate(LocalDate.now().minusYears(28));
                user.setGender(Gender.MALE);
                user.setActivityLevel(ActivityLevel.VERY_ACTIVE);
                user.setGoal(FitnessGoal.LOSE);
                user.setNutritionStyle("Keto");
                users.save(user);
                if (nutritionPlans.findByUserOrderByStartDateDesc(user).isEmpty()) {
                    NutritionPlan plan = new NutritionPlan();
                    plan.setUser(user);
                    plan.setName("Balanceado");
                    plan.setDailyCalories(user.getDailyCalorieGoal());
                    plan.setProteinPercent(BigDecimal.valueOf(30));
                    plan.setCarbsPercent(BigDecimal.valueOf(40));
                    plan.setFatPercent(BigDecimal.valueOf(30));
                    plan.setProteinGoalGrams(user.getProteinGoalGrams());
                    plan.setCarbsGoalGrams(user.getCarbsGoalGrams());
                    plan.setFatGoalGrams(user.getFatGoalGrams());
                    plan.setStartDate(LocalDate.now().minusYears(1));
                    nutritionPlans.save(plan);
                }

                Food chicken = foods.findByBarcode("7790000000011").orElseThrow();
                Food rice = foods.findByBarcode("7790000000028").orElseThrow();
                Food yogurt = foods.findByBarcode("7790000000042").orElseThrow();
                addLog(foodLogs, user, chicken, MealType.LUNCH, 150);
                addLog(foodLogs, user, rice, MealType.LUNCH, 100);
                addLog(foodLogs, user, yogurt, MealType.AFTERNOON_SNACK, 200);
                WaterLog water = new WaterLog();
                water.setUser(user);
                water.setLogDate(LocalDate.now());
                water.setLiters(BigDecimal.valueOf(1.5));
                waterLogs.save(water);
            }
        };
    }

    private static void ensureAdmin(UserRepository users, PasswordEncoder passwordEncoder) {
        if (users.existsByEmailIgnoreCase("admin@gmail.com")) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setFullName("Admin");
        admin.setEmail("admin@gmail.com");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setRole(Role.ADMIN);
        admin.setNutritionStyle("Balanceado");
        users.save(admin);
    }

    private static void ensureFood(FoodRepository foods, String name, String brand, String barcode, FoodCategory category,
            int calories, double protein, double carbs, double fat, FoodPreparation preparation, String preparationSource, Set<String> tags) {
        var existing = foods.findByBarcode(barcode);
        if (existing.isPresent()) {
            Food food = existing.get();
            food.setPreparation(preparation);
            food.setPreparationSource(preparationSource);
            foods.save(food);
            return;
        }
        foods.save(food(name, brand, barcode, category, calories, protein, carbs, fat, preparation, preparationSource, tags));
    }

    private static Food food(String name, String brand, String barcode, FoodCategory category, int calories,
            double protein, double carbs, double fat, FoodPreparation preparation, String preparationSource, Set<String> tags) {
        Food food = new Food();
        food.setName(name);
        food.setBrand(brand);
        food.setBarcode(barcode);
        food.setCategory(category);
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setCalories(calories);
        food.setProteinGrams(BigDecimal.valueOf(protein));
        food.setCarbsGrams(BigDecimal.valueOf(carbs));
        food.setFatGrams(BigDecimal.valueOf(fat));
        food.setPreparation(preparation);
        food.setPreparationSource(preparationSource);
        food.setTags(tags);
        return food;
    }

    private static void setServing(FoodRepository foods, String barcode, String name, double grams) {
        foods.findByBarcode(barcode).ifPresent(food -> {
            food.setServingName(name);
            food.setServingWeightGrams(BigDecimal.valueOf(grams));
            foods.save(food);
        });
    }

    private static void setPreparationGroup(FoodRepository foods, String group, String... barcodes) {
        for (String barcode : barcodes) {
            foods.findByBarcode(barcode).ifPresent(food -> {
                food.setPreparationGroup(group);
                foods.save(food);
            });
        }
    }

    private static void addLog(FoodLogRepository foodLogs, AppUser user, Food food, MealType mealType, int quantity) {
        BigDecimal ratio = BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(100));
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setFood(food);
        log.setMealType(mealType);
        log.setLogDate(LocalDate.now());
        log.setQuantity(BigDecimal.valueOf(quantity));
        log.setUnit(FoodUnit.GRAM);
        log.setCalories(BigDecimal.valueOf(food.getCalories()).multiply(ratio).intValue());
        log.setProteinGrams(food.getProteinGrams().multiply(ratio));
        log.setCarbsGrams(food.getCarbsGrams().multiply(ratio));
        log.setFatGrams(food.getFatGrams().multiply(ratio));
        foodLogs.save(log);
    }
}
