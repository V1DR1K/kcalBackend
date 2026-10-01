package com.scalegrams.profile;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NutritionPlanTimelineTests {
    private NutritionPlan plan(String start, String end, NutritionPlanStatus status) {
        NutritionPlan plan = new NutritionPlan(); plan.setStartDate(LocalDate.parse(start));
        plan.setEndDate(end == null ? null : LocalDate.parse(end)); plan.setStatus(status); return plan;
    }
    @Test void derivesEffectiveEndsWithoutChangingDeclaredDates() {
        NutritionPlan first = plan("2026-01-01", null, NutritionPlanStatus.SCHEDULED);
        NutritionPlan second = plan("2026-02-01", null, NutritionPlanStatus.SCHEDULED);
        assertThat(NutritionPlanTimeline.effectiveEnd(first, List.of(first, second))).isEqualTo(LocalDate.parse("2026-01-31"));
        assertThat(first.getEndDate()).isNull();
    }
    @Test void alternativesAndArchivedPlansDoNotShortenTheCurrentWindow() {
        NutritionPlan first = plan("2026-01-01", null, NutritionPlanStatus.SCHEDULED);
        assertThat(NutritionPlanTimeline.effectiveEnd(first, List.of(first,
                plan("2026-02-01", null, NutritionPlanStatus.ALTERNATIVE), plan("2026-03-01", null, NutritionPlanStatus.ARCHIVED)))).isNull();
    }
    @Test void cancellingPlansInEitherOrderRecomputesFromDeclaredDates() {
        NutritionPlan first = plan("2026-01-01", null, NutritionPlanStatus.SCHEDULED);
        NutritionPlan second = plan("2026-02-01", null, NutritionPlanStatus.ALTERNATIVE);
        NutritionPlan third = plan("2026-03-01", null, NutritionPlanStatus.SCHEDULED);
        assertThat(NutritionPlanTimeline.effectiveEnd(first, List.of(first, second, third))).isEqualTo(LocalDate.parse("2026-02-28"));
        third.setStatus(NutritionPlanStatus.ALTERNATIVE);
        assertThat(NutritionPlanTimeline.effectiveEnd(first, List.of(first, second, third))).isNull();
    }
    @Test void preservesDeclaredGaps() {
        NutritionPlan first = plan("2026-01-01", "2026-01-15", NutritionPlanStatus.SCHEDULED);
        NutritionPlan second = plan("2026-02-01", null, NutritionPlanStatus.SCHEDULED);
        assertThat(NutritionPlanTimeline.effectiveEnd(first, List.of(first, second))).isEqualTo(first.getEndDate());
    }
}
