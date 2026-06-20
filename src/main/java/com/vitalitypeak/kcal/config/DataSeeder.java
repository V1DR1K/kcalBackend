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
                    165, 31, 0, 3.6, Set.of("Alta en Proteina", "Keto Friendly"));
            ensureFood(foods, "Arroz Blanco", "Generico", "7790000000028", FoodCategory.CEREAL,
                    130, 2.7, 28, 0.3, Set.of("Carbohidrato"));
            ensureFood(foods, "Palta (Aguacate)", "Fresco", "7790000000035", FoodCategory.FAT,
                    160, 2, 8.5, 14.7, Set.of("Grasas Saludables"));
            ensureFood(foods, "Yogur Griego Natural", "KazaFitness Dairy", "7790000000042", FoodCategory.DAIRY,
                    59, 10, 3.6, 0.4, Set.of("Proteina"));
            ensureFood(foods, "Atun en lata", "Mar Azul", "7790000000059", FoodCategory.PROTEIN,
                    116, 26, 0, 1, Set.of("Alta Proteina", "Keto"));
            ensureFood(foods, "Banana", "Fresco", "7790000000066", FoodCategory.FRUIT,
                    89, 1.1, 22.8, 0.3, Set.of("Fruta"));

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
            int calories, double protein, double carbs, double fat, Set<String> tags) {
        if (foods.existsByBarcode(barcode)) {
            return;
        }
        foods.save(food(name, brand, barcode, category, calories, protein, carbs, fat, tags));
    }

    private static Food food(String name, String brand, String barcode, FoodCategory category, int calories,
            double protein, double carbs, double fat, Set<String> tags) {
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
        food.setTags(tags);
        return food;
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
