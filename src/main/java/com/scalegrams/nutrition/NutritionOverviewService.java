package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.nutrition.NutritionDtos.DashboardResponse;
import com.scalegrams.nutrition.NutritionDtos.DaySummary;
import com.scalegrams.nutrition.NutritionDtos.FoodLogResponse;
import com.scalegrams.nutrition.NutritionDtos.HistoryResponse;
import com.scalegrams.nutrition.NutritionDtos.MacroProgress;
import com.scalegrams.nutrition.NutritionDtos.MealSummary;
import com.scalegrams.nutrition.NutritionDtos.MealTypeResponse;
import com.scalegrams.nutrition.NutritionDtos.NutrientValueResponse;
import com.scalegrams.profile.NutritionPlan;
import com.scalegrams.profile.ProfileService;
import com.scalegrams.user.AppUser;

@Service
public class NutritionOverviewService {
    private final FoodLogRepository foodLogs;
    private final ProfileService profileService;
    private final NutritionService nutritionService;

    public NutritionOverviewService(FoodLogRepository foodLogs, ProfileService profileService,
            NutritionService nutritionService) {
        this.foodLogs = foodLogs;
        this.profileService = profileService;
        this.nutritionService = nutritionService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        NutritionPlan plan = profileService.resolvePlan(user, targetDate);
        List<FoodLog> logs = foodLogs.findByUserAndLogDate(user, targetDate);
        BigDecimal protein = sum(logs, FoodLog::getProteinGrams);
        BigDecimal carbs = sum(logs, FoodLog::getCarbsGrams);
        BigDecimal fat = sum(logs, FoodLog::getFatGrams);
        Map<String, NutrientValueResponse> dailyNutrients = new LinkedHashMap<>();
        logs.forEach(log -> nutritionService.mergeNutrients(dailyNutrients,
                log.getNutrientSnapshot().stream().map(nutritionService::toNutrientResponse).toList()));
        int calories = logs.stream().map(FoodLog::getCalories).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        Map<MealType, List<FoodLog>> byMeal = logs.stream().collect(Collectors.groupingBy(FoodLog::getMealType));
        List<MealSummary> meals = Arrays.stream(MealType.values()).map(meal -> {
            List<FoodLogResponse> items = byMeal.getOrDefault(meal, List.of()).stream()
                    .map(nutritionService::toFoodLogResponse).toList();
            BigDecimal mealProtein = sumResponses(items, FoodLogResponse::proteinGrams);
            BigDecimal mealCarbs = sumResponses(items, FoodLogResponse::carbsGrams);
            BigDecimal mealFat = sumResponses(items, FoodLogResponse::fatGrams);
            int mealCalories = items.stream().map(FoodLogResponse::calories).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).sum();
            return new MealSummary(meal, label(meal), mealCalories, mealProtein, mealCarbs, mealFat, items);
        }).toList();
        return new DashboardResponse(targetDate, plan.getDailyCalories(), calories,
                Math.max(0, plan.getDailyCalories() - calories),
                List.of(
                        progress("protein", "Proteina", protein, BigDecimal.valueOf(plan.getProteinGoalGrams())),
                        progress("carbs", "Carbohidratos", carbs, BigDecimal.valueOf(plan.getCarbsGoalGrams())),
                        progress("fat", "Grasas", fat, BigDecimal.valueOf(plan.getFatGoalGrams()))),
                meals, profileService.activePlan(user, targetDate), dailyNutrients.values().stream().toList());
    }

    @Transactional(readOnly = true)
    public List<MealTypeResponse> mealTypes() {
        return Arrays.stream(MealType.values()).map(meal -> new MealTypeResponse(meal, label(meal))).toList();
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(AppUser user, int year, int month) {
        YearMonth monthRange = YearMonth.of(year, month);
        List<FoodLogRepository.DayNutritionProjection> summaries = foodLogs.summarizeByDate(user,
                monthRange.atDay(1), monthRange.atEndOfMonth());
        Map<LocalDate, FoodLogRepository.DayNutritionProjection> byDate = summaries.stream()
                .collect(Collectors.toMap(FoodLogRepository.DayNutritionProjection::getDate, item -> item));
        List<NutritionPlan> plans = profileService.plansForRange(user, monthRange.atDay(1), monthRange.atEndOfMonth());
        LocalDate today = LocalDate.now();
        List<DaySummary> days = monthRange.atDay(1).datesUntil(monthRange.atEndOfMonth().plusDays(1)).map(date -> {
            FoodLogRepository.DayNutritionProjection summary = byDate.get(date);
            long count = summary == null ? 0 : summary.getRecordCount();
            boolean energyComplete = count > 0 && summary.getEnergyCount() == count;
            boolean proteinComplete = count > 0 && summary.getProteinCount() == count;
            boolean carbsComplete = count > 0 && summary.getCarbsCount() == count;
            boolean fatComplete = count > 0 && summary.getFatCount() == count;
            boolean complete = energyComplete && proteinComplete && carbsComplete && fatComplete;
            int calories = summary == null ? 0 : Math.toIntExact(summary.getCalories());
            BigDecimal protein = summary == null ? BigDecimal.ZERO : NutritionMath.scale(summary.getProteinGrams());
            BigDecimal carbs = summary == null ? BigDecimal.ZERO : NutritionMath.scale(summary.getCarbsGrams());
            BigDecimal fat = summary == null ? BigDecimal.ZERO : NutritionMath.scale(summary.getFatGrams());
            NutritionPlan plan = profileService.resolvePlanFromRange(user, date, plans);
            return new DaySummary(date, calories, plan.getDailyCalories(), proteinComplete ? protein : null,
                    carbsComplete ? carbs : null, fatComplete ? fat : null,
                    !date.isAfter(today) && energyComplete && calories <= plan.getDailyCalories(), plan.getId(),
                    plan.getName(), count, energyComplete, complete,
                    count == 0 ? "NONE" : complete ? "COMPLETE" : "PARTIAL", protein, carbs, fat);
        }).toList();
        List<DaySummary> eligible = days.stream()
                .filter(day -> !day.date().isAfter(today) && day.recordCount() > 0 && day.energyComplete()).toList();
        Integer average = eligible.isEmpty() ? null : (int) Math.round(eligible.stream()
                .mapToInt(DaySummary::caloriesConsumed).average().orElseThrow());
        long completed = eligible.stream().filter(DaySummary::goalReached).count();
        return new HistoryResponse(year, month, days, average, completed, eligible.size());
    }

    private static BigDecimal sum(List<FoodLog> logs, java.util.function.Function<FoodLog, BigDecimal> mapper) {
        return logs.stream().map(mapper).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal sumResponses(List<FoodLogResponse> logs,
            java.util.function.Function<FoodLogResponse, BigDecimal> mapper) {
        return logs.stream().map(mapper).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, java.math.RoundingMode.HALF_UP);
    }

    private static MacroProgress progress(String key, String label, BigDecimal consumed, BigDecimal goal) {
        return new MacroProgress(key, label, consumed, goal, goal.subtract(consumed).max(BigDecimal.ZERO));
    }

    private static String label(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> "Desayuno";
            case LUNCH -> "Almuerzo";
            case AFTERNOON_SNACK -> "Merienda";
            case DINNER -> "Cena";
        };
    }
}
