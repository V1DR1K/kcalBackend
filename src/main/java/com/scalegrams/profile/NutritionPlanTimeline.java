package com.scalegrams.profile;

import java.time.LocalDate;
import java.util.List;

/** Effective windows are projections: declared dates remain untouched. */
public final class NutritionPlanTimeline {
    private NutritionPlanTimeline() {}
    public static LocalDate effectiveEnd(NutritionPlan plan, List<NutritionPlan> timeline) {
        if (plan.getStatus() != NutritionPlanStatus.SCHEDULED) return plan.getEndDate();
        LocalDate next = timeline.stream().filter(item -> item.getStatus() == NutritionPlanStatus.SCHEDULED
                && item.getStartDate().isAfter(plan.getStartDate())).map(NutritionPlan::getStartDate)
                .min(LocalDate::compareTo).map(date -> date.minusDays(1)).orElse(null);
        LocalDate declared = plan.getEndDate();
        return declared == null ? next : next == null || declared.isBefore(next) ? declared : next;
    }
}
