package com.vitalitypeak.kcal.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vitalitypeak.kcal.catalog.Food;
import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.catalog.FoodRepository;
import com.vitalitypeak.kcal.catalog.FoodUnit;
import com.vitalitypeak.kcal.common.NotFoundException;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddFoodLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddWaterRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.DashboardResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.DaySummary;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodLogResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.HistoryResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.MacroProgress;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.MealSummary;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.vitalitypeak.kcal.user.AppUser;

@Service
public class NutritionService {
    private final FoodRepository foods;
    private final FoodLogRepository foodLogs;
    private final WaterLogRepository waterLogs;

    public NutritionService(FoodRepository foods, FoodLogRepository foodLogs, WaterLogRepository waterLogs) {
        this.foods = foods;
        this.foodLogs = foodLogs;
        this.waterLogs = waterLogs;
    }

    public List<FoodResponse> searchFoods(String query, FoodCategory category) {
        List<Food> result;
        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery && category != null) {
            result = foods.findByNameContainingIgnoreCaseAndCategory(query, category);
        } else if (hasQuery) {
            result = foods.findByNameContainingIgnoreCase(query);
        } else if (category != null) {
            result = foods.findByCategory(category);
        } else {
            result = foods.findAll();
        }
        return result.stream().map(this::toFoodResponse).toList();
    }

    public FoodResponse findFood(Long id) {
        return toFoodResponse(getFood(id));
    }

    public FoodResponse findByBarcode(String barcode) {
        return foods.findByBarcode(barcode).map(this::toFoodResponse)
                .orElseThrow(() -> new NotFoundException("No encontramos un alimento con ese codigo."));
    }

    public NutritionPreviewResponse preview(Long foodId, BigDecimal quantity, FoodUnit unit) {
        return preview(getFood(foodId), quantity);
    }

    @Transactional
    public FoodLogResponse addFoodLog(AppUser user, AddFoodLogRequest request) {
        Food food = getFood(request.foodId());
        NutritionPreviewResponse preview = preview(food, request.quantity());
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setFood(food);
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(request.unit());
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        log.setCalories(preview.calories());
        log.setProteinGrams(preview.proteinGrams());
        log.setCarbsGrams(preview.carbsGrams());
        log.setFatGrams(preview.fatGrams());
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public void addWater(AppUser user, AddWaterRequest request) {
        WaterLog log = new WaterLog();
        log.setUser(user);
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        log.setLiters(request.liters());
        waterLogs.save(log);
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        List<FoodLog> logs = foodLogs.findByUserAndLogDate(user, targetDate);
        int calories = logs.stream().mapToInt(FoodLog::getCalories).sum();
        BigDecimal protein = sum(logs, FoodLog::getProteinGrams);
        BigDecimal carbs = sum(logs, FoodLog::getCarbsGrams);
        BigDecimal fat = sum(logs, FoodLog::getFatGrams);
        BigDecimal water = waterLogs.findByUserAndLogDate(user, targetDate).stream()
                .map(WaterLog::getLiters).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<MealType, List<FoodLog>> byMeal = logs.stream().collect(Collectors.groupingBy(FoodLog::getMealType));
        List<MealSummary> meals = Arrays.stream(MealType.values()).map(meal -> {
            List<FoodLogResponse> items = byMeal.getOrDefault(meal, List.of()).stream().map(this::toFoodLogResponse).toList();
            int mealCalories = items.stream().mapToInt(FoodLogResponse::calories).sum();
            return new MealSummary(meal, label(meal), mealCalories, items);
        }).toList();
        return new DashboardResponse(targetDate, user.getDailyCalorieGoal(), calories,
                Math.max(0, user.getDailyCalorieGoal() - calories),
                List.of(
                        progress("protein", "Proteina", protein, BigDecimal.valueOf(user.getProteinGoalGrams())),
                        progress("carbs", "Carbohidratos", carbs, BigDecimal.valueOf(user.getCarbsGoalGrams())),
                        progress("fat", "Grasas", fat, BigDecimal.valueOf(user.getFatGoalGrams()))),
                meals, water, user.getWaterGoalLiters());
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(AppUser user, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<FoodLog> logs = foodLogs.findByUserAndLogDateBetween(user, ym.atDay(1), ym.atEndOfMonth());
        Map<LocalDate, List<FoodLog>> byDate = logs.stream().collect(Collectors.groupingBy(FoodLog::getLogDate));
        List<DaySummary> days = ym.atDay(1).datesUntil(ym.atEndOfMonth().plusDays(1)).map(date -> {
            List<FoodLog> dayLogs = byDate.getOrDefault(date, List.of());
            int calories = dayLogs.stream().mapToInt(FoodLog::getCalories).sum();
            return new DaySummary(date, calories, user.getDailyCalorieGoal(), sum(dayLogs, FoodLog::getProteinGrams),
                    sum(dayLogs, FoodLog::getCarbsGrams), sum(dayLogs, FoodLog::getFatGrams),
                    calories > 0 && calories <= user.getDailyCalorieGoal());
        }).toList();
        int average = days.stream().filter(day -> day.caloriesConsumed() > 0).mapToInt(DaySummary::caloriesConsumed)
                .average().stream().mapToInt(value -> (int) Math.round(value)).findFirst().orElse(0);
        long completed = days.stream().filter(DaySummary::goalReached).count();
        return new HistoryResponse(year, month, days, average, completed);
    }

    private Food getFood(Long foodId) {
        return foods.findById(foodId).orElseThrow(() -> new NotFoundException("Alimento no encontrado."));
    }

    private NutritionPreviewResponse preview(Food food, BigDecimal quantity) {
        BigDecimal ratio = quantity.divide(food.getBaseQuantity(), 4, RoundingMode.HALF_UP);
        return new NutritionPreviewResponse(
                BigDecimal.valueOf(food.getCalories()).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue(),
                scale(food.getProteinGrams().multiply(ratio)),
                scale(food.getCarbsGrams().multiply(ratio)),
                scale(food.getFatGrams().multiply(ratio)));
    }

    private MacroProgress progress(String key, String label, BigDecimal consumed, BigDecimal goal) {
        return new MacroProgress(key, label, consumed, goal, goal.subtract(consumed).max(BigDecimal.ZERO));
    }

    private FoodResponse toFoodResponse(Food food) {
        return new FoodResponse(food.getId(), food.getName(), food.getBrand(), food.getBarcode(), food.getCategory(),
                food.getBaseUnit(), food.getBaseQuantity(), food.getCalories(), food.getProteinGrams(), food.getCarbsGrams(),
                food.getFatGrams(), food.getImageUrl(), food.getTags());
    }

    private FoodLogResponse toFoodLogResponse(FoodLog log) {
        return new FoodLogResponse(log.getId(), log.getLogDate(), log.getMealType(), toFoodResponse(log.getFood()),
                log.getQuantity(), log.getUnit(), log.getCalories(), log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams());
    }

    private static BigDecimal sum(List<FoodLog> logs, java.util.function.Function<FoodLog, BigDecimal> mapper) {
        return logs.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static String label(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> "Desayuno";
            case LUNCH -> "Almuerzo";
            case DINNER -> "Cena";
            case SNACK -> "Snack / Otros";
        };
    }
}
